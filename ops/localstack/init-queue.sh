#!/bin/sh
# Creates the outbound queue LocalStack needs for local dev; runs once on container start.
awslocal sqs create-queue --queue-name sendtrace-outbound
