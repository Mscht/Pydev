'''
Entry point module to run code-coverage.
'''


def is_valid_py_file(path):
    '''
    Checks whether the file can be read by the coverage module. This is especially
    needed for .pyx files and .py files with syntax errors.
    '''
    import os

    isValid = False
    if os.path.isfile(path) and not os.path.splitext(path)[1] == '.pyx':
        try:
            with open(path, 'r') as f:
                compile(f.read(), path, 'exec')
                isValid = True
        except:
            pass
    return isValid


def execute():
    import os
    import sys

    files = None
    if 'combine' not in sys.argv:

        if '--pydev-analyze' in sys.argv:

            #Ok, what we want here is having the files passed through stdin (because
            #there may be too many files for passing in the command line -- we could
            #just pass a dir and make the find files here, but as that's already
            #given in the java side, let's just gather that info here).
            sys.argv.remove('--pydev-analyze')
            try:
                s = raw_input()
            except:
                s = input()
            s = s.replace('\r', '')
            s = s.replace('\n', '')
            all_files = s.split('|')

            files = [v for v in all_files if is_valid_py_file(v)]
            invalid_files = [v for v in all_files if v and v not in files]
            if invalid_files:
                sys.stderr.write('Invalid files not passed to coverage: %s'
                                 % ', '.join(invalid_files))

            #Note that in this case we'll already be in the working dir with the coverage files, 
            #so, the coverage file location is not passed.

        else:
            #For all commands, the coverage file is configured in pydev, and passed as the first 
            #argument in the command line, so, let's make sure this gets to the coverage module.
            os.environ['COVERAGE_FILE'] = sys.argv[1]
            del sys.argv[1]

    try:
        import coverage #@UnresolvedImport
    except:
        sys.stderr.write('Error: coverage module could not be imported\n')
        sys.stderr.write('Please make sure that the coverage module '
                         '(http://nedbatchelder.com/code/coverage/)\n')
        sys.stderr.write('is properly installed in your interpreter: %s\n' % (sys.executable,))

        import traceback;traceback.print_exc()
        return

    if hasattr(coverage, '__version__'):
        version = tuple(map(int, coverage.__version__.split('.')[:2]))
        if version < (4, 3):
            sys.stderr.write('Error: minimum supported coverage version is 4.3.'
                             '\nFound: %s\nLocation: %s' 
                             % ('.'.join(str(x) for x in version), coverage.__file__))
            sys.exit(1)
    else:
        sys.stderr.write('Warning: Could not determine version of python module coverage.'
                         '\nEnsure coverage version is >= 4.3')

    from coverage.cmdline import main #@UnresolvedImport

    if files is not None:
        sys.argv.append('xml')
        sys.argv += files

    main()

if __name__ == '__main__':
    execute()