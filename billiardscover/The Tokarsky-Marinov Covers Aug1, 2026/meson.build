# We set all the compiler flags ourself, so use the plain buildtype
project('cover', 'cpp', default_options : ['buildtype=plain'])

compiler_id = meson.get_compiler('cpp').get_id()

# Set the warning flags
if compiler_id == 'clang'
    add_project_arguments(['-Weverything',
                          '-Wno-padded', '-Wno-comma',
                          '-Wno-global-constructors', '-Wno-exit-time-destructors',
                          '-Wno-c++98-compat', '-Wno-c++98-compat-pedantic',
                          '-Wno-disabled-macro-expansion',
                          '-std=c++14'],
                          language : 'cpp')
elif compiler_id == 'gcc'
    add_project_arguments(['-Wall', '-Wextra', '-Wpedantic',
                          '-Wno-c++11-compat', '-Wno-c++14-compat',
                          '-std=c++14'],
                          language : 'cpp')
else
    error(compiler_id + ' is not supported; please use GCC or Clang')
endif

headers = include_directories('src/backend/headers')

sources = ['src/backend/cpp/code_sequence.cpp',
          'src/backend/cpp/code_type.cpp',
          'src/backend/cpp/cover.cpp',
          'src/backend/cpp/division.cpp',
          'src/backend/cpp/equations.cpp',
          'src/backend/cpp/evaluator.cpp',
          'src/backend/cpp/general.cpp',
          'src/backend/cpp/inequalities.cpp',
          'src/backend/cpp/math/symbols.cpp',
          'src/backend/cpp/parse.cpp',
          'src/backend/cpp/region.cpp',
          'src/backend/cpp/shooting_vectors.cpp',
          'src/backend/cpp/trig_identities.cpp',
          'src/backend/cpp/unfolding.cpp',
          'src/backend/cpp/util.cpp',
          'src/backend/cpp/verify.cpp',
          'src/backend/cpp/wrapper.cpp']

test_headers = include_directories('src/test/headers')

executable('cover', ['src/cover/cpp/main.cpp'] + sources,
           include_directories : headers,
           # These options are the same between clang and gcc
           # TODO fsanitize=integer overflow?
           cpp_args : ['-DNDEBUG', '-O3', '-march=native', '-flto', '-ftrapv'],
           link_args : ['-lgmp', '-lmpfr', '-ltbb', '-ljemalloc'])

# The test backend can be compiled with different flags
test_backend = executable('test_backend', ['src/test/cpp/main.cpp'] + sources,
                          include_directories : [headers, test_headers],
                          cpp_args : ['-g', '-fsanitize=undefined',
                                      '-O3', '-march=native', '-flto'],
                          link_args : ['-lgmp', '-lmpfr', '-ltbb',
                                       '-lboost_unit_test_framework',
                                       '-fsanitize=undefined'])
test('test backend', test_backend)
