#!/bin/bash

# Bash "strict mode", to help catch problems and bugs in the shell
# script. Every bash script you write should include this. See
# http://redsymbol.net/articles/unofficial-bash-strict-mode/ for
# details.
set -euo pipefail
# Tell apt-get we're never going to be able to give manual
# feedback:
export DEBIAN_FRONTEND=noninteractive


apt-get update 
# apt-get upgrade -y
apt-get -y install 
apt-get -y install software-properties-common ca-certificates wget openjdk-8-jre
add-apt-repository ppa:jonathonf/ffmpeg-4 
apt-get install -y ffmpeg
apt-get clean
apt-get autoremove

wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-6.2.2.deb --no-check-certificate
apt-get install -y ./elasticsearch-6.2.2.deb 
# service elasticsearch start

cd /
cd ./Audimus
apt-get install -y ./*.deb
apt-get install -y -f 

cd /
cd ./AudimusServer
apt-get install -y ./*.deb
apt-get install -y -f 

# WIP: having issues with data transfer to volumes. At this stage /voiceinteraction has been created but for dev purpose
# I mkdir again just in case and chown the directory for the LangPack installers
cd /
mkdir -p /voiceinteraction
chown audimus:audimus /voiceinteraction

cd /
cd ./LangPack 
apt-get -o Dpkg::Options::="--force-overwrite" install ./*.deb
cd /


