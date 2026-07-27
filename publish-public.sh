#!/bin/sh

(
  cd src/
  ./gradlew :odysseus:publishMavenCentralPublicationToSonatypeCentralRepository
)

