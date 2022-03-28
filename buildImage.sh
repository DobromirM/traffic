rm -rf dist/
mkdir -p dist/

cd server
./gradlew build

cd ../
tar -xf server/build/distributions/swim-traffic-3.11.0.tar -C dist/

cd ui
npm install
npm run compile && npm run bundle
mkdir -p ../dist/swim-traffic-3.11.0/ui
cp -rf index.html dist ../dist/swim-traffic-3.11.0/ui

cd ../

docker build ./ -f ./java.Dockerfile -t swimdatafabric/traffic:1.0
