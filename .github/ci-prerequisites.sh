#!/usr/bin/env bash
set -e

echo "::group::Reclaiming disk space"
sudo docker image prune --all --force || true
sudo rm -rf /usr/share/dotnet || true
sudo rm -rf /usr/share/swift || true
sudo rm -rf /usr/local/lib/android || true
sudo rm -rf /opt/ghc || true
sudo rm -rf /usr/local/.ghcup || true
sudo rm -rf /opt/pipx || true
sudo rm -rf /usr/share/rust || true
sudo rm -rf /usr/local/go || true
sudo rm -rf /usr/share/miniconda || true
sudo rm -rf /usr/local/share/powershell || true
sudo rm -rf /usr/lib/google-cloud-sdk || true
sudo rm -rf /opt/hostedtoolcache/CodeQL || true
echo "::endgroup::"

echo "Disk space after cleanup:"
df -h /
