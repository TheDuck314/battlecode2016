#!/bin/bash

file="$1"

config=~/.battlecode.ui

cat $config | egrep -v '^choice=|^file=' >$config
echo choice=FILE >>$config
echo file="$file" >>$config

make
