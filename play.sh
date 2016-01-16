#!/bin/bash

file="$1"

config=~/.battlecode.ui

mv -v $config "$config".old

cat $config | egrep -v '^choice=|^file=' >$config
echo choice=FILE >>$config
echo file="$file" >>$config
make

mv -v "$config".old $config
