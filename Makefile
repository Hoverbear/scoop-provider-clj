all:
		docker rm -f provider || true
		docker rmi provider || true
		lein immutant war
		docker build -t provider .

run:
	docker run --name provider --rm -t -i -p 127.0.0.1:8082:8080 -p 127.0.0.1:9992:9990 -p 127.0.0.1:8889:8888 --link keycloak:auth --link provider-db:database provider
