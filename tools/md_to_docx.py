import sys
import os
import re
import zipfile
from xml.sax.saxutils import escape


def md_to_paragraphs(md_text):
    lines = md_text.splitlines()
    paras = []
    current = []
    for line in lines:
        line = line.rstrip()
        if line == '':
            if current:
                paras.append(' '.join(current))
                current = []
        else:
            # remove markdown headers and list bullets
            line = re.sub(r'^\s{0,3}#{1,6}\s+', '', line)
            line = re.sub(r'^\s*[-*+]\s+', '', line)
            current.append(line)
    if current:
        paras.append(' '.join(current))
    return paras


def build_document_xml(paragraphs):
    parts = []
    parts.append('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>')
    parts.append('<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">')
    parts.append('<w:body>')
    for p in paragraphs:
        text = escape(p)
        parts.append('<w:p><w:r><w:t xml:space="preserve">{}</w:t></w:r></w:p>'.format(text))
    parts.append('<w:sectPr><w:pgSz w:w="12240" w:h="15840"/></w:sectPr>')
    parts.append('</w:body>')
    parts.append('</w:document>')
    return '\n'.join(parts)


def main():
    if len(sys.argv) < 3:
        print('Usage: md_to_docx.py input.md output.docx')
        sys.exit(1)
    input_md = sys.argv[1]
    output_docx = sys.argv[2]
    if not os.path.exists(input_md):
        print('Input file not found:', input_md)
        sys.exit(2)
    with open(input_md, 'r', encoding='utf-8') as f:
        md = f.read()
    paras = md_to_paragraphs(md)
    document_xml = build_document_xml(paras)

    content_types = '''<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>'''

    rels = '''<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="/word/document.xml"/>
</Relationships>'''

    # Create minimal docx
    with zipfile.ZipFile(output_docx, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('[Content_Types].xml', content_types)
        z.writestr('_rels/.rels', rels)
        z.writestr('word/document.xml', document_xml)
    print('Wrote', output_docx)

if __name__ == '__main__':
    main()
