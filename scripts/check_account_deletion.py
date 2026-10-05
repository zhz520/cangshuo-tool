"""Verify deletion using two disposable accounts and real MySQL cascades. No real users are changed."""
import secrets
import time
from check_auth_api import request, sql

def main():
    emails=["qa-delete-"+secrets.token_hex(12)+"@example.com" for _ in range(2)]
    password=secrets.token_urlsafe(24)
    ids=[]; count=0
    def check(value):
        nonlocal count
        assert value,"Account deletion check failed (sensitive details omitted)"
        count+=1
    try:
        data=[]
        for email in emails:
            code,envelope=request("auth/register",dict(email=email,password=password,nickname="QA deletion"))
            check(code==201); data.append(envelope["data"]); ids.append(int(envelope["data"]["user"]["id"]))
        a,b=ids; token=data[0]["accessToken"]
        check(request("auth/me",dict(email=emails[0],password=password),method="DELETE")[0]==401)
        check(request("sync/push",dict(deviceId="a"*32,items=[dict(entityType="FAVORITE",entityKey="calculator",
            updatedAt=int(time.time()*1000),deleted=False,payload={})]),token)[0]==200)
        check(request("feedback",dict(type="OTHER",content="Temporary deletion QA"),token)[0]==201)
        sql(f"INSERT INTO ai_daily_usage VALUES({a},UTC_DATE(),1)")
        for email,pw in [(emails[1],password),(emails[0],"wrong-password")]:
            check(request("auth/me",dict(email=email,password=pw,id=b),token,method="DELETE")[0]==401)
        check(request("auth/me",token=token)[0]==200)
        check(request("auth/me",dict(email=emails[0].upper(),password=password,id=b),token,port=8088,method="DELETE")[0]==200)
        check(request("auth/me",token=token)[0]==401)
        check(request("auth/refresh",dict(refreshToken=data[0]["refreshToken"]))[0]==401)
        check(request("auth/me",token=data[1]["accessToken"])[0]==200)
        for table in ["auth_refresh_session","user_sync_state","user_sync_entity","user_feedback","ai_daily_usage"]:
            check(sql(f"SELECT COUNT(*) FROM {table} WHERE user_id={a}")=="0")
        check(sql(f"SELECT COUNT(*) FROM auth_refresh_token WHERE session_id NOT IN(SELECT id FROM auth_refresh_session)")=="0")
        check(sql(f"SELECT COUNT(*) FROM deleted_account WHERE user_id={a}")=="1")
        code,recreated=request("auth/register",dict(email=emails[0],password=password,nickname="QA recreated"))
        check(code==201 and int(recreated["data"]["user"]["id"])>max(ids))
        ids.append(int(recreated["data"]["user"]["id"]))
    finally:
        for email in emails:sql(f"DELETE FROM sys_user WHERE email='{email}'")
        for user in ids:sql(f"DELETE FROM deleted_account WHERE user_id={user}")
    print(f"Account deletion: {count}/{count} checks passed; disposable data cleaned.")

if __name__ == "__main__":main()
