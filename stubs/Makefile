all:
	rm -rf out/*
	mkdir -p out
	cp ../app.jar out
	cp -r ../generatedstubs out
	cd out && jar xvf app.jar && rm app.jar && cd ..
	find . -name *.java | xargs javac -cp .:out -d out
	cd out && jar cvf app.jar *
