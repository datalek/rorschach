#!/bin/bash -e

echo "********************************"
echo "Running some tests with coverage"
echo "********************************"
sbt clean coverage test coverageReport
# aggregate coverage
sbt coverageAggregate