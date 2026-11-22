import subprocess,json,select,time
p=subprocess.Popen(['/home/pazfernando/.opencode/bin/opencode','acp'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True,bufsize=1)
def call(i,method,params,timeout=45):
 p.stdin.write(json.dumps(dict(jsonrpc='2.0',id=i,method=method,params=params))+'\n');p.stdin.flush()
 end=time.time()+timeout
 while time.time()<end:
  if select.select([p.stdout],[],[],1)[0]:
   line=p.stdout.readline()
   if not line:raise RuntimeError('ACP closed')
   d=json.loads(line)
   if d.get('method')=='session/update':
    u=d['params']['update']
    if u.get('sessionUpdate')=='agent_message_chunk':print('response:',u.get('content',{}).get('text',''),flush=True)
   if d.get('id')==i:return d
 raise TimeoutError(method)
try:
 r=call(1,'initialize',{'protocolVersion':1,'clientCapabilities':{},'clientInfo':{'name':'openclaw-preflight','version':'1'}})
 print('initialize',r.get('result',{}).get('agentInfo'),flush=True)
 r=call(2,'session/new',{'cwd':'/home/pazfernando/projects/acquiring-simulator','mcpServers':[]})
 sid=r['result']['sessionId']
 r=call(3,'session/set_config_option',{'sessionId':sid,'configId':'model','value':'nan/deepseek-v4-flash'})
 print('model selection',r.get('error') or [(x['id'],x.get('currentValue')) for x in r['result'].get('configOptions',[])],flush=True)
 if r.get('error'):raise RuntimeError('Model not selected')
 print('prompt result',call(4,'session/prompt',{'sessionId':sid,'prompt':[{'type':'text','text':'Connection preflight only. Do not use tools, read or write files. Reply exactly ACP_OK.'}]},45),flush=True)
finally:p.terminate()
