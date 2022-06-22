## Requirements
Prometheus Server outside of VoltDB cluster - https://prometheus.io/docs/prometheus/latest/installation/ \
Grafana outside of VoltDB cluster - https://grafana.com/docs/grafana/latest/installation/

## Build Instructions
`git clone https://github.com/ssomagani/volt-prometheus-grafana` \
`cd volt-prometheus-grafana` \
`ant` 

## Run Instructions
### Install VoltDB-Prometheus Agent \
Unzip the voltdb-prometheus.zip file on any node in your VoltDB cluster

### Run VoltDB-Prometheus Agent \
Change directory to voltdb-prometheus and run ./voltdb-prometheus

### Connect Prometheus server to VoltDB-Prometheus Agent \
Under the prometheus server installation directory, find prometheus.yml and add new static target to voltdb_server:1234 - https://prometheus.io/docs/introduction/first_steps/

### Create Prometheus Data Source in Grafana - https://prometheus.io/docs/visualization/grafana/

### Load pre-built Grafana dashboard to view VoltDB Statistics - https://grafana.com/docs/grafana/latest/dashboards/export-import/
