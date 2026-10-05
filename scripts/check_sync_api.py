"""Exercise real account-isolated sync APIs with ephemeral users and no printed credentials."""
from concurrent.futures import ThreadPoolExecutor
import secrets
import time
from check_auth_api import request, sql

CHECKS=0


def check(value):
    global CHECKS
    assert value,"Sync API check failed (response omitted)"
    CHECKS+=1


def main():
    emails=["sync-check-"+secrets.token_hex(12)+"@example.invalid" for _ in range(2)]
    now=int(time.time()*1000)
    devices=["1"*32,"2"*32]
    def item(code="calculator",at=now,deleted=False):
        return dict(entityType="FAVORITE",entityKey=code,updatedAt=at,deleted=deleted,payload={})
    def push(token,items,device=devices[0]):
        return request("sync/push",dict(deviceId=device,items=items),token)
    def pull(token,cursor="",port=8081):
        return request("sync/pull"+("?cursor="+cursor if cursor else ""),token=token,port=port)
    try:
        tokens=[]
        for email in emails:
            status,body=request("auth/register",dict(email=email,password=secrets.token_urlsafe(24),nickname="sync"))
            check(status==201); tokens.append(body["data"]["accessToken"])
        a,b=tokens
        check(pull(None)[0]==401)
        check(push(a,[item()])[1]["data"]["applied"]==1)
        check(push(a,[item()])[1]["data"]["applied"]==0)
        status,page=pull(a,port=8088); check(status==200 and len(page["data"]["items"])==1)
        cursor=page["data"]["nextCursor"]
        check(pull(a,cursor)[1]["data"]["items"]==[])
        check(pull(b)[1]["data"]["items"]==[])
        check(pull(b,cursor)[0]==400)
        check(push(a,[item(at=now+1,deleted=True)],devices[1])[1]["data"]["applied"]==1)
        check(push(a,[item()],devices[0])[1]["data"]["applied"]==0)
        check(pull(a,cursor)[1]["data"]["items"][0]["deleted"])
        check(push(a,[item(at=now+1)],devices[0])[1]["data"]["applied"]==0)
        with ThreadPoolExecutor(2) as pool:
            results=list(pool.map(lambda device:push(a,[item(at=now+2,deleted=device==devices[1])],device),devices))
        check(all(s==200 for s,_ in results))
        winner=pull(a)[1]["data"]["items"][0]
        check(winner["deviceId"]==devices[1] and winner["deleted"])
        cursor=pull(a)[1]["data"]["nextCursor"]
        check(push(a,[item("badclock",at=now+400000)])[0]==400)
        check(push(a,[item("batchfirst"),item("second",at=0)])[0]==400)
        check(pull(a,cursor)[1]["data"]["items"]==[])
        records=[item(f"tool_{n}") for n in range(101)]
        check(push(a,records)[0]==400)
        check(push(a,records[:100])[0]==200); check(push(a,records[100:])[0]==200)
        status,first=pull(a,cursor); first=first["data"]
        check(status==200 and first["hasMore"] and len(first["items"])==100)
        status,second=pull(a,first["nextCursor"]);second=second["data"]
        check(status==200 and not second["hasMore"] and len(second["items"])==1)
        check(pull(a,second["nextCursor"])[1]["data"]["items"]==[])
        check(pull(b)[1]["data"]["items"]==[])
        recent=dict(entityType="RECENT",entityKey="calculator",updatedAt=now+10,deleted=False,payload=dict(lastUsedAt=now,useCount=7))
        check(push(a,[recent])[0]==200)
        current=pull(a,second["nextCursor"])[1]["data"]
        check(current["items"][0]["payload"]["useCount"]==7)
        recent_deleted=dict(entityType="RECENT",entityKey="calculator",updatedAt=now+11,deleted=True,payload={})
        check(push(a,[recent_deleted],devices[1])[0]==200)
        check(push(a,[recent])[1]["data"]["applied"]==0)
        changed=pull(a,current["nextCursor"])[1]["data"]
        check(changed["items"][0]["entityType"]=="RECENT" and changed["items"][0]["deleted"])
        check(pull(b)[1]["data"]["items"]==[])
        settings=[dict(entityType="SETTING",entityKey=key,updatedAt=now+20,deleted=False,payload=dict(value=value))
                  for key,value in [("theme","DARK"),("language","en"),("grid_columns","3"),("startup_page","FAVORITES")]]
        check(push(a,settings)[0]==200)
        page=pull(a,changed["nextCursor"])[1]["data"]
        check(len(page["items"])==4 and {i["entityKey"]:i["payload"]["value"] for i in page["items"]}==
              {"theme":"DARK","language":"en","grid_columns":"3","startup_page":"FAVORITES"})
        bad=dict(entityType="SETTING",entityKey="sync_enabled",updatedAt=now+21,deleted=False,payload=dict(value="true"))
        check(push(a,[bad])[0]==400)
        bad["entityKey"]="theme";check(push(a,[bad])[0]==400)
        check(pull(b)[1]["data"]["items"]==[])
        check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success=1")=="1")
    finally:
        for email in emails: sql(f"DELETE FROM sys_user WHERE email='{email}'")
    print(f"Sync API: {CHECKS}/{CHECKS} checks passed; temporary users removed.")


if __name__=="__main__": main()
