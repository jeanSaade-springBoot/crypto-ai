import argparse, hashlib, os, re
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Preformatted

def esc(s):
    return s.replace('&','&amp;').replace('<','&lt;').replace('>','&gt;')

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--source',required=True);ap.add_argument('--output',required=True);ap.add_argument('--revision',required=True);ap.add_argument('--codex',default='UNKNOWN');ap.add_argument('--claude',default='UNKNOWN');ap.add_argument('--status',default='NOT AUTHORIZED');args=ap.parse_args()
    raw=open(args.source,'rb').read(); text=raw.decode('utf-8',errors='replace'); sha=hashlib.sha256(raw).hexdigest()
    styles=getSampleStyleSheet(); styles.add(ParagraphStyle(name='CenterSmall',parent=styles['BodyText'],alignment=TA_CENTER,fontSize=8,leading=10)); styles.add(ParagraphStyle(name='CodeSmall',parent=styles['Code'],fontSize=7,leading=9))
    doc=SimpleDocTemplate(args.output,pagesize=A4,rightMargin=16*mm,leftMargin=16*mm,topMargin=15*mm,bottomMargin=15*mm,title=os.path.basename(args.source))
    story=[Paragraph(esc(os.path.basename(args.source)),styles['Title']),Spacer(1,4*mm)]
    meta=[["Revision",args.revision],["SHA-256",sha],["Codex verdict",args.codex],["Claude verdict",args.claude],["Authorization",args.status]]
    t=Table(meta,colWidths=[35*mm,135*mm]);t.setStyle(TableStyle([('GRID',(0,0),(-1,-1),0.25,colors.grey),('VALIGN',(0,0),(-1,-1),'TOP'),('FONTNAME',(0,0),(0,-1),'Helvetica-Bold'),('FONTSIZE',(0,0),(-1,-1),7)]));story += [t,Spacer(1,5*mm)]
    in_code=False; code=[]
    for line in text.splitlines():
        if line.strip().startswith('```'):
            if in_code:
                story.append(Preformatted('\n'.join(code),styles['CodeSmall']));code=[];in_code=False
            else: in_code=True
            continue
        if in_code: code.append(line); continue
        if not line.strip(): story.append(Spacer(1,2*mm)); continue
        m=re.match(r'^(#{1,4})\s+(.*)',line)
        if m:
            level=len(m.group(1)); style=styles['Heading1' if level==1 else 'Heading2' if level==2 else 'Heading3'];story.append(Paragraph(esc(m.group(2)),style));continue
        if line.lstrip().startswith(('- ','* ')):
            story.append(Paragraph('• '+esc(line.strip()[2:]),styles['BodyText']));continue
        story.append(Paragraph(esc(line),styles['BodyText']))
    if code: story.append(Preformatted('\n'.join(code),styles['CodeSmall']))
    story += [PageBreak(),Paragraph('FROZEN ARTIFACT CONTROL',styles['Heading1']),Paragraph(f'Revision: {esc(args.revision)}',styles['BodyText']),Paragraph(f'SHA-256: {sha}',styles['BodyText']),Paragraph(f'Codex: {esc(args.codex)}',styles['BodyText']),Paragraph(f'Claude: {esc(args.claude)}',styles['BodyText']),Paragraph(f'Implementation authorization: {esc(args.status)}',styles['BodyText']),Spacer(1,4*mm),Paragraph('Any material change to the source document invalidates this frozen revision and requires a new dual-agent review.',styles['BodyText'])]
    doc.build(story)
    print(f'PDF={args.output}');print(f'SHA256={sha}')
if __name__=='__main__': main()
