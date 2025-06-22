#!/bin/bash

# Generate 2048-bit RSA private key
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem

# Extract public key from private key
openssl rsa -pubout -in private.pem -out public.pem