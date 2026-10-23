#!/bin/bash
set -e
./build.sh
java -cp bin TestPassBook
