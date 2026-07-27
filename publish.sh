#!/bin/sh

(
  cd src/
  ./gradlew :odysseus:publishReleasePublicationToGitHubPackagesRepository
)

