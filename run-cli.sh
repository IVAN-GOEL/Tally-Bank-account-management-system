#!/bin/bash
set -e
./build.sh
exec java -cp bin Main
