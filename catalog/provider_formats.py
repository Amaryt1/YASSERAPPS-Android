"""Decode authorized, documented source feeds without guessing release metadata."""
import json
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
def local(tag):
    return tag.rsplit("}",1)[-1]
def xml_node(node):
    data={"@"+local(k):v for k,v in node.attrib.items()}
    for child in node:
        key=local(child.tag)
        value=xml_node(child)
        if key in data:
            if not isinstance(data[key],list):data[key]=[data[key]]
            data[key].append(value)
        else:data[key]=value
    content=(node.text or "").strip()
    if not data:return content
    if content:data["text"]=content
    return data
class StructuredPage(HTMLParser):
    def __init__(self):
        super().__init__();self.docs=[];self.meta={};self.capture=None
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=="script" and a.get("type","").lower()=="application/ld+json":self.capture=[]
        if tag=="meta" and a.get("property","").startswith("og:") and a.get("content"):self.meta[a["property"]]=a["content"]
    def handle_data(self,data):
        if self.capture is not None:self.capture.append(data)
    def handle_endtag(self,tag):
        if tag=="script" and self.capture is not None:
            try:self.docs.append(json.loads("".join(self.capture)))
            except ValueError:pass
            self.capture=None
def application_nodes(obj,depth=0):
    if depth>20:return
    if isinstance(obj,list):
        for x in obj:yield from application_nodes(x,depth+1)
    elif isinstance(obj,dict):
        types=obj.get("@type",[])
        if isinstance(types,str):types=[types]
        if isinstance(types,list) and any(x in ("SoftwareApplication","MobileApplication","VideoGame") for x in types):
            yield obj
        else:
            for k in ("@graph","mainEntity","itemListElement","item"):
                if k in obj:yield from application_nodes(obj[k],depth+1)
def decode(text,kind):
    if kind=="json":return json.loads(text)
    if kind=="html-jsonld":
        p=StructuredPage();p.feed(text)
        return {"apps":[dict(x,openGraph=p.meta) for doc in p.docs for x in application_nodes(doc)],"next":None}
    if kind not in ("xml","rss","atom"):raise ValueError("Unsupported feed format")
    if "<!DOCTYPE" in text.upper() or "<!ENTITY" in text.upper():raise ValueError("Unsafe XML")
    root=ET.fromstring(text)
    if kind=="rss":
        if local(root.tag)!="rss":raise ValueError("Not RSS")
        return {"apps":[xml_node(x) for x in root.iter() if local(x.tag)=="item"],"next":None}
    if kind=="atom":
        if local(root.tag)!="feed":raise ValueError("Not Atom")
        nxt=next((x.get("href") for x in root if local(x.tag)=="link" and x.get("rel")=="next"),None)
        return {"apps":[xml_node(x) for x in root if local(x.tag)=="entry"],"next":nxt}
    # Generic XML only reads explicitly supplied application records.
    return {"apps":[xml_node(x) for x in root if local(x.tag) in ("app","application","game","release")],"next":root.get("next")}
