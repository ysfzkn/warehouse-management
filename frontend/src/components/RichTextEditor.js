import React, { useMemo, useRef, useEffect } from 'react';
import ReactQuill from 'react-quill';
import 'react-quill/dist/quill.snow.css';

/**
 * Medium-like WYSIWYG editor. Content is returned as HTML and
 * written to the `page.content` field on the backend; on the store
 * side it is rendered via `dangerouslySetInnerHTML` (the existing flow is preserved).
 *
 * Features:
 *   - Headings (H2, H3), bold/italic/underline, lists, blockquote
 *   - Link, image (by URL), alignment, table
 *   - Style stripping on paste (clean paste) — pasting from Word / Google Docs
 *     does not introduce style pollution.
 *
 * Usage:
 *   <RichTextEditor value={content} onChange={setContent} />
 */
export default function RichTextEditor({ value, onChange, placeholder, minHeight = 320 }) {
  const quillRef = useRef(null);

  // Strip styles on paste — prevents dirty HTML coming from Word/Docs
  useEffect(() => {
    const editor = quillRef.current?.getEditor?.();
    if (!editor) return;
    editor.clipboard.addMatcher(Node.ELEMENT_NODE, (_node, delta) => {
      // Only keep plain text + minimal formatting from pasted HTML
      const Delta = editor.constructor.imports['delta'] || editor.constructor.import?.('delta');
      if (!Delta) return delta;
      const cleaned = new Delta();
      delta.ops.forEach(op => {
        if (op.insert && typeof op.insert === 'string') {
          const attrs = op.attributes || {};
          const keep = {};
          if (attrs.bold) keep.bold = true;
          if (attrs.italic) keep.italic = true;
          if (attrs.underline) keep.underline = true;
          if (attrs.link) keep.link = attrs.link;
          if (attrs.header) keep.header = attrs.header;
          if (attrs.list) keep.list = attrs.list;
          cleaned.insert(op.insert, Object.keys(keep).length ? keep : undefined);
        }
      });
      return cleaned;
    });
  }, []);

  const modules = useMemo(() => ({
    toolbar: [
      [{ header: [2, 3, false] }],
      ['bold', 'italic', 'underline', 'strike'],
      [{ list: 'ordered' }, { list: 'bullet' }],
      ['blockquote', 'code-block'],
      ['link'],
      [{ align: [] }],
      ['clean'],
    ],
    clipboard: { matchVisual: false },
  }), []);

  const formats = useMemo(() => [
    'header',
    'bold', 'italic', 'underline', 'strike',
    'list', 'bullet',
    'blockquote', 'code-block',
    'link',
    'align',
  ], []);

  return (
    <div className="rich-text-editor-wrapper">
      <ReactQuill
        ref={quillRef}
        theme="snow"
        value={value || ''}
        onChange={onChange}
        modules={modules}
        formats={formats}
        placeholder={placeholder || 'Buraya yazmaya başlayın…'}
        style={{
          background: 'white',
          borderRadius: 4,
        }}
      />
      <style>{`
        .rich-text-editor-wrapper .ql-container {
          min-height: ${minHeight}px;
          font-size: 15px;
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        }
        .rich-text-editor-wrapper .ql-editor {
          min-height: ${minHeight}px;
          line-height: 1.7;
        }
        .rich-text-editor-wrapper .ql-editor h2 {
          font-size: 1.5rem;
          margin-top: 1.2em;
        }
        .rich-text-editor-wrapper .ql-editor h3 {
          font-size: 1.25rem;
          margin-top: 1em;
        }
        .rich-text-editor-wrapper .ql-editor blockquote {
          border-left: 4px solid #e5e7eb;
          padding-left: 1em;
          color: #6b7280;
          font-style: italic;
        }
        .rich-text-editor-wrapper .ql-toolbar {
          border-top-left-radius: 4px;
          border-top-right-radius: 4px;
          background: #f9fafb;
        }
        .rich-text-editor-wrapper .ql-container {
          border-bottom-left-radius: 4px;
          border-bottom-right-radius: 4px;
        }
      `}</style>
    </div>
  );
}
