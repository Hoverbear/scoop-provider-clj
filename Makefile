all:
		docker rm -f provider || true
		docker rmi provider || true
		lein immutant war
		docker build -t provider .

run:
	docker run --name provider --rm -t -i -h provider.scoop.local -p 127.0.0.1:9992:9990 -p 127.0.0.1:8889:8888 --link auth:auth.scoop.local --link visualizer:visualizer.scoop.local --link provider-db:database provider
