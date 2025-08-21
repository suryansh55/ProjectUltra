LD_LIBRARY_PATH=./backend/shared \
java -Dprism.order=sw -Dprism.verbose=true \
     -Djna.library.path=build/libs/backend/shared \
     -Djava.library.path=build/libs/backend/shared \
     -jar ./billiard-viewer.jar