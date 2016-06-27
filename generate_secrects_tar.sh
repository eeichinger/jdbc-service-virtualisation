#!/usr/bin/env bash

# this script creates a Travis-encrypted tar and expects the following files are present in a folder ./local/
#
# id_rsa_travisci
#    - private ssh key for GitHub Travis user
# local.gpg.pubring.gpg
#    - public GPG keyring for code-signing
# local.gpg.secring.gpg
#    - private GPG keyring for code-signing

cd local

# exclude MacOS-specific stuff (http://stackoverflow.com/questions/8766730/tar-command-in-mac-os-x-adding-hidden-files-why)
export COPYFILE_DISABLE=true

tar cvf gpg.secrets.tar *.gpg id_rsa_travisci

travis encrypt-file gpg.secrets.tar

rm gpg.secrets.tar

cp gpg.secrets.tar.enc ../
