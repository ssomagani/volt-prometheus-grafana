## Requirements
Prometheus Server outside of VoltDB cluster - https://prometheus.io/docs/prometheus/latest/installation/ \
Grafana outside of VoltDB cluster - https://grafana.com/docs/grafana/latest/installation/

## Build Instructions
`git clone https://github.com/ssomagani/volt-prometheus-grafana` \
`cd volt-prometheus-grafana` \
`ant` 

## Run Instructions
1. Install VoltDB-Prometheus Agent \
1.1 Unzip the voltdb-prometheus.zip file on any node in your VoltDB cluster

2. Run VoltDB-Prometheus Agent \
2.1 Change directory to voltdb-prometheus and run ./voltdb-prometheus

3. Connect Prometheus server to VoltDB-Prometheus Agent \
3.1 Under the prometheus server installation directory, find prometheus.yml and add new static target to voltdb_server:1234 - https://prometheus.io/docs/introduction/first_steps/

4. Create Prometheus Data Source in Grafana - https://prometheus.io/docs/visualization/grafana/

5. Load pre-built Grafana dashboard to view VoltDB Statistics - https://grafana.com/docs/grafana/latest/dashboards/export-import/
