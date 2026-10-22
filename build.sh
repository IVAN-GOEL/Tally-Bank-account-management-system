#!/bin/bash
set -e
mkdir -p bin
javac -d bin src/*.java
echo "Compilation successful! Bytecode generated in bin/"
