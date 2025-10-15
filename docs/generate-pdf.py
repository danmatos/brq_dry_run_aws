#!/usr/bin/env python3
"""
PDF Generator for ETL Production Deployment Documentation
Converts Markdown to professional PDF with styling
"""

import os
import sys
from pathlib import Path
import markdown
from weasyprint import HTML, CSS
from datetime import datetime

def create_pdf_styles():
    """Create CSS styles for professional PDF formatting"""
    return """
    @page {
        size: A4;
        margin: 2cm;
        @top-center {
            content: "ETL Production Deployment - Lessons Learned";
            font-size: 10pt;
            color: #666;
        }
        @bottom-center {
            content: "Page " counter(page) " of " counter(pages);
            font-size: 10pt;
            color: #666;
        }
    }
    
    body {
        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        line-height: 1.6;
        color: #333;
        font-size: 11pt;
    }
    
    h1 {
        color: #2c3e50;
        border-bottom: 3px solid #3498db;
        padding-bottom: 10px;
        page-break-after: avoid;
        font-size: 24pt;
        margin-top: 30px;
    }
    
    h2 {
        color: #34495e;
        border-bottom: 2px solid #ecf0f1;
        padding-bottom: 5px;
        page-break-after: avoid;
        font-size: 18pt;
        margin-top: 25px;
    }
    
    h3 {
        color: #2c3e50;
        page-break-after: avoid;
        font-size: 14pt;
        margin-top: 20px;
        font-weight: bold;
    }
    
    h4 {
        color: #7f8c8d;
        font-size: 12pt;
        margin-top: 15px;
        font-weight: bold;
    }
    
    code {
        background-color: #f8f9fa;
        border: 1px solid #e9ecef;
        border-radius: 3px;
        padding: 2px 4px;
        font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
        font-size: 10pt;
        color: #e74c3c;
    }
    
    pre {
        background-color: #f8f9fa;
        border: 1px solid #e9ecef;
        border-radius: 5px;
        padding: 15px;
        overflow-x: auto;
        page-break-inside: avoid;
        font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
        font-size: 9pt;
        line-height: 1.4;
    }
    
    pre code {
        background: none;
        border: none;
        padding: 0;
        color: #2c3e50;
    }
    
    blockquote {
        border-left: 4px solid #3498db;
        margin: 0;
        padding-left: 20px;
        font-style: italic;
        color: #7f8c8d;
        background-color: #f8f9fa;
        padding: 10px 20px;
        border-radius: 0 5px 5px 0;
    }
    
    table {
        border-collapse: collapse;
        width: 100%;
        margin: 20px 0;
        page-break-inside: avoid;
        font-size: 10pt;
    }
    
    th, td {
        border: 1px solid #ddd;
        padding: 8px 12px;
        text-align: left;
    }
    
    th {
        background-color: #3498db;
        color: white;
        font-weight: bold;
    }
    
    tr:nth-child(even) {
        background-color: #f2f2f2;
    }
    
    ul, ol {
        margin: 15px 0;
        padding-left: 30px;
    }
    
    li {
        margin: 5px 0;
    }
    
    .error-box {
        border: 2px solid #e74c3c;
        background-color: #fdf2f2;
        padding: 15px;
        border-radius: 5px;
        margin: 15px 0;
        page-break-inside: avoid;
    }
    
    .success-box {
        border: 2px solid #27ae60;
        background-color: #f2fdf2;
        padding: 15px;
        border-radius: 5px;
        margin: 15px 0;
        page-break-inside: avoid;
    }
    
    .warning-box {
        border: 2px solid #f39c12;
        background-color: #fdf9f2;
        padding: 15px;
        border-radius: 5px;
        margin: 15px 0;
        page-break-inside: avoid;
    }
    
    .page-break {
        page-break-before: always;
    }
    
    .no-break {
        page-break-inside: avoid;
    }
    
    .center {
        text-align: center;
    }
    
    .highlight {
        background-color: #fff3cd;
        padding: 2px 4px;
        border-radius: 3px;
    }
    
    /* Emojis and icons styling */
    .emoji {
        font-size: 1.2em;
    }
    
    /* Footer styling */
    .footer {
        margin-top: 40px;
        padding-top: 20px;
        border-top: 1px solid #ddd;
        font-size: 9pt;
        color: #666;
        text-align: center;
    }
    """

def convert_md_to_pdf(md_file_path, output_pdf_path):
    """Convert Markdown file to PDF with professional styling"""
    
    try:
        # Read markdown content
        with open(md_file_path, 'r', encoding='utf-8') as file:
            md_content = file.read()
        
        # Configure markdown extensions
        md = markdown.Markdown(extensions=[
            'extra',           # Tables, code blocks, etc.
            'codehilite',      # Syntax highlighting
            'toc',             # Table of contents
            'attr_list',       # Attribute lists
            'def_list',        # Definition lists
            'abbr',            # Abbreviations
            'footnotes',       # Footnotes
            'md_in_html',      # Markdown in HTML
        ])
        
        # Convert markdown to HTML
        html_content = md.convert(md_content)
        
        # Add HTML wrapper with metadata
        html_document = f"""
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>ETL Production Deployment - Lessons Learned</title>
            <meta name="author" content="BRQ Itaú - ETL Team">
            <meta name="subject" content="Production Deployment Documentation">
            <meta name="keywords" content="ETL, AWS, EKS, MSK, Spring Boot, Kubernetes, Fargate, EC2">
            <meta name="creator" content="ETL Documentation Generator">
            <meta name="producer" content="WeasyPrint">
            <meta name="creation-date" content="{datetime.now().isoformat()}">
        </head>
        <body>
            <div class="header">
                <h1 class="center">🚀 ETL System Production Deployment</h1>
                <h2 class="center">Lessons Learned & Solutions Documentation</h2>
                <div class="center">
                    <strong>BRQ Itaú - ETL Team</strong><br>
                    Generated: {datetime.now().strftime('%d/%m/%Y %H:%M')}
                </div>
                <hr>
            </div>
            
            {html_content}
            
            <div class="footer">
                <hr>
                <p><strong>Document Information</strong></p>
                <p>Generated on: {datetime.now().strftime('%d/%m/%Y at %H:%M:%S')}</p>
                <p>Project: ETL System - BRQ Itaú | Stack: Spring Boot 3.2.2 + Kotlin + AWS EKS + MSK</p>
                <p>Status: ✅ Production Operational</p>
            </div>
        </body>
        </html>
        """
        
        # Create CSS styling
        css_styles = CSS(string=create_pdf_styles())
        
        # Convert HTML to PDF
        html_doc = HTML(string=html_document)
        html_doc.write_pdf(output_pdf_path, stylesheets=[css_styles])
        
        print(f"✅ PDF successfully generated: {output_pdf_path}")
        print(f"📄 File size: {os.path.getsize(output_pdf_path) / 1024:.1f} KB")
        
    except Exception as e:
        print(f"❌ Error generating PDF: {e}")
        return False
    
    return True

def main():
    """Main function to generate PDF documentation"""
    
    # Define paths
    base_dir = Path(__file__).parent
    md_file = base_dir / "ETL_Production_Deployment_Lessons_Learned.md"
    pdf_file = base_dir / "ETL_Production_Deployment_Lessons_Learned.pdf"
    
    print("🔄 Starting PDF generation...")
    print(f"📁 Source: {md_file}")
    print(f"📄 Output: {pdf_file}")
    
    # Check if markdown file exists
    if not md_file.exists():
        print(f"❌ Markdown file not found: {md_file}")
        return 1
    
    # Generate PDF
    success = convert_md_to_pdf(md_file, pdf_file)
    
    if success:
        print("\n🎉 PDF Generation Complete!")
        print(f"📂 Location: {pdf_file.absolute()}")
        print("\n📋 Document Contents:")
        print("   • Executive Summary")
        print("   • 7 Critical Errors & Solutions")
        print("   • Architecture Comparison")
        print("   • Lessons Learned")
        print("   • Production Metrics")
        print("   • Future Recommendations")
        return 0
    else:
        print("\n❌ PDF Generation Failed!")
        return 1

if __name__ == "__main__":
    sys.exit(main())
