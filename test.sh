#!/bin/bash
if [ ! -d "bin" ]; then
    ./build.sh
fi
java -cp bin TestPassBook
