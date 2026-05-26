#


## How to test config endpoint

```bash
# Local
curl http://127.0.0.1:8000/api/tracker/drivers/edgarsegundo/config/

# Prod
curl -H "Accept: application/json" \
     -H "X-API-Key: 649dba..." \
     "https://sys.fastvistos.com.br/api/tracker/drivers/edgarsegundo/config/"
```