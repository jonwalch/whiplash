export DATOMIC_SYSTEM=whiplash-datomic
export DATOMIC_REGION=us-west-2
export DATOMIC_SOCKS_PORT=8182

curl -x socks5h://localhost:$DATOMIC_SOCKS_PORT http://entry.$DATOMIC_SYSTEM.$DATOMIC_REGION.datomic.net:8182/