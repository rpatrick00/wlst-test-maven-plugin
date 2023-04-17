"""
 _wlst_test_driver.py - this script is the test driver script
     for the WLST Test Maven Plugin's test goal.  It sets up
     the WLST environment, and then builds and executes the
     test suite out of the project's test modules.

 Copyright 2018 Robert Patrick <rhpatrick@gmail.com>

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
"""
import unittest
import os
from sets import Set
import sys

import wlstModule as wlst

import java.lang.System as JavaSystem

_WLST_TEST_PLUGIN_DEBUG_PROPERTY_NAME = 'wlst.test.plugin.debug'
_debug = False


def run_tests(verbosity_level, test_files):
    """
    Execute the unit tests using the specified list of test files.
    :param verbosity_level: output level for the test runner
    :param test_files: list of test files
    :return:
    """
    suite = unittest.TestSuite()
    for test_file in test_files:
        filename = os.path.basename(test_file)
        filename_without_extension = os.path.splitext(filename)[0]
        test_module = __import__(filename_without_extension)
        if _debug:
            print 'Adding test module %s defined by file %s to the test suite' % (filename_without_extension, test_file)
        suite.addTest(unittest.defaultTestLoader.loadTestsFromModule(test_module))

    result = unittest.TextTestRunner(verbosity=verbosity_level).run(suite)
    return result

def _compute_python_path(main_execute_dir, test_execute_dir, test_files):
    path_list = [
        main_execute_dir.replace('\\', '/'),
        test_execute_dir.replace('\\', '/')
    ]

    if test_files is not None and type(test_files) is list:
        for test_file in test_files:
            path_list.append(os.path.dirname(test_file).replace('\\', '/'))
        path_list = list(Set(path_list))

    return path_list

def _silence_wlst():
    wlst.WLS.setLogToStdOut(False)
    wlst.WLS.setShowLSResult(False)
    wlst.WLS_ON.setlogToStandardOut(False)
    wlst.WLS_ON.setHideDumpStack('true')
    wlst.WLS.getCommandExceptionHandler().setMode(True)
    wlst.WLS.getCommandExceptionHandler().setSilent(True)
    return

def main():
    global _debug

    _silence_wlst()

    debug_value = JavaSystem.getProperty(_WLST_TEST_PLUGIN_DEBUG_PROPERTY_NAME, 'false')
    if debug_value == 'true':
        _debug = True
        print 'WLST Test Driver arguments:'
        for index, arg in enumerate(sys.argv):
            print '    sys.argv[%s] = %s' % (str(index), arg)
        print '\nWLST Test Driver environment:'
        for env_name, env_value in os.environ.iteritems():
            print '    %s = %s' % (str(env_name), str(env_value))

    # The first three arg are:
    #     - the location of the python files being tested
    #     - the location of the python test files to be executed
    #     - the verbosity level for the unittest suite
    #
    # All additional args are the test files to execute.
    #
    main_execute_dir = sys.argv[1]
    test_execute_dir = sys.argv[2]
    test_verbosity = int(sys.argv[3])
    test_files = list(sys.argv[4:])
    paths = _compute_python_path(main_execute_dir, test_execute_dir, test_files)

    for path in paths:
        if _debug:
            print 'Appending %s to python path' % path
        sys.path.append(path)

    result = run_tests(test_verbosity, test_files)
    if not result.wasSuccessful():
        sys.exit(2)

if __name__ == "main" or __name__ == "__main__":
    main()
