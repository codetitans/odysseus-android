#!/bin/sh

version=${1:-0.0.10}
bundle_name=odysseus-$version-bundle.zip
bundle_path=bundle/pl/codetitans/odysseus/
repo_path=~/.m2/repository/pl/codetitans/odysseus/$version/

echo Building...
rm -rf ~/.m2/repository/pl/codetitans/odysseus/$version/
ODYSSEUS_VERSION=$version ./gradlew :odysseus:publishMavenCentralPublicationToMavenLocal

######################

generate_checksums() {
  local dir="$1"

  for f in "$dir"/*; do
    [ -f "$f" ] || continue
    case "$f" in
      *.md5|*.sha1) continue ;;
    esac

    if command -v md5 >/dev/null 2>&1; then
      md5 -q "$f" > "$f.md5"                      # macOS/BSD: -q = hash only, no file name
    else
      md5sum "$f" | awk '{print $1}' > "$f.md5"   # Linux: remove file name
    fi

    shasum -a 1 "$f" | awk '{print $1}' > "$f.sha1" # shasum always appends file name -> awk removes it
  done
}


######################

# copy artifacts

echo Copying artifacts...
rm -rf bundle/
mkdir -p "$bundle_path"
cp -r "$repo_path" "$bundle_path/$version/"

# generate checksums
echo Generating checksums...
generate_checksums "$bundle_path/$version/"

# create a bundle
echo Creating bundle "$bundle_name"...
rm -f $bundle_name
(
  cd bundle/
  zip -r -X "../$bundle_name" pl/ -x '*.DS_Store'
)

