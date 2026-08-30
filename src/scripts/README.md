# Scripts
The following contains C++ command line programs emulating certain billiards-viewer functionality 
for use on Digitial Research Alliance of Canada (DRAC) HPC clusters.

Currently supported functionality includes:
- vary3cmd (Vary3 + VaryCS)
- vary4cmd (Vary4 + VaryCS)
- varyAutoPolyCmd (LiLuMaxVary)

# Compilation
For use on DRAC clusters, it is recommended for each C++ program to be compiled on the server it will be used.
## Requirements
The necessary libraries should be a subset of the libraries required by the C++ backend of billiards-everything.
Programs are built using C++23

On **Windows**, see the latest 'Billiards jar Windows Setup Guide' for a complete list of libraries. 
The `makefile` expects each `.dll` to be in a subdirectory of `scripts` called `libs`

On **Linux**, the same set of libraries is expected to be available on the `LD_LIBRARY_PATH` path

# The **.dll** or **.so** billiards-everything C++ backend must be in the same directory as the Makefile during compilation (or in the generated `build/` directory) **AS WELL AS** in the same directory as the resulting programs in order to run.

## Commands
To compile a specific program, use the following format for the make command, where `program_to_compile` 
is one of the entries listed under "Currently supported functionality includes" (vary3cmd, vary4cmd, varyAutoPolyCmd, etc.)

```
make PROG=[program_to_compile]
```

Example: `make PROG=vary3cmd` to build vary3cmd

# Usage
Each compiled program runs a single instance of its corresponding calculation (vary3cmd will run a Vary3 calculation on all its given command line arguments)
These programs are often used in conjunction with a scripting/job harness (See '4A-scripts') to sync, submit and run multiple jobs to DRAC clusters on-command remotely.

> Updated 7/8/2026
