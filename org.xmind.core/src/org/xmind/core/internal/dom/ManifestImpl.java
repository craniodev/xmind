/* ******************************************************************************
 * Copyright (c) 2006-2008 XMind Ltd. and others.
 * 
 * This file is a part of XMind 3. XMind releases 3 and
 * above are dual-licensed under the Eclipse Public License (EPL),
 * which is available at http://www.eclipse.org/legal/epl-v10.html
 * and the GNU Lesser General Public License (LGPL), 
 * which is available at http://www.gnu.org/licenses/lgpl.html
 * See http://www.xmind.net/license.html for details.
 * 
 * Contributors:
 *     XMind Ltd. - initial API and implementation
 *******************************************************************************/
package org.xmind.core.internal.dom;

import static org.xmind.core.internal.dom.DOMConstants.ATTR_FULL_PATH;
import static org.xmind.core.internal.dom.DOMConstants.ATTR_MEDIA_TYPE;
import static org.xmind.core.internal.dom.DOMConstants.TAG_FILE_ENTRY;
import static org.xmind.core.internal.dom.DOMConstants.TAG_MANIFEST;
import static org.xmind.core.internal.dom.InternalDOMUtils.getParentPath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmind.core.Core;
import org.xmind.core.IEncryptionData;
import org.xmind.core.IFileEntry;
import org.xmind.core.IWorkbook;
import org.xmind.core.internal.Manifest;
import org.xmind.core.util.DOMUtils;
import org.xmind.core.util.FileUtils;

public class ManifestImpl extends Manifest {

    private static final Collection<IFileEntry> NO_ENTRIES = Collections
            .emptyList();

    private static final Comparator<String> ENTRY_COMPARATOR = new Comparator<String>() {

        public int compare(String o1, String o2) {
            return o1.compareToIgnoreCase(o2);
        }

    };

    private Document implementation;

    private WorkbookImpl ownedWorkbook;

    private Map<String, IFileEntry> entries = null;

    public ManifestImpl(Document implementation) {
        this.implementation = implementation;
        init();
    }

    /**
     * 
     * @param workbook
     */
    protected void setWorkbook(WorkbookImpl workbook) {
        this.ownedWorkbook = workbook;
    }

    private void init() {
        Element m = DOMUtils.ensureChildElement(implementation, TAG_MANIFEST);
        NS.setNS(NS.Manifest, m);
    }

    public Document getImplementation() {
        return implementation;
    }

    public Element getManifestElement() {
        return implementation.getDocumentElement();
    }

    public Object getAdapter(Class adapter) {
        if (adapter == Document.class || adapter == Node.class)
            return implementation;
        if (adapter == IWorkbook.class)
            return ownedWorkbook;
        return super.getAdapter(adapter);
    }

    public IWorkbook getOwnedWorkbook() {
        return ownedWorkbook;
    }

    protected Collection<IFileEntry> getAllRegisteredEntries() {
        if (entries == null)
            return NO_ENTRIES;
        return entries.values();
    }

    public List<IFileEntry> getFileEntries() {
        Element ms = getManifestElement();
        List<IFileEntry> list = new ArrayList<IFileEntry>(ms.getChildNodes()
                .getLength());
        Iterator<Element> it = DOMUtils.childElementIterByTag(ms,
                TAG_FILE_ENTRY);
        while (it.hasNext()) {
            Element e = it.next();
            if (e.hasAttribute(ATTR_FULL_PATH)) {
                String path = e.getAttribute(ATTR_FULL_PATH);
                if (entries == null)
                    entries = new TreeMap<String, IFileEntry>(ENTRY_COMPARATOR);
                IFileEntry entry = entries.get(path);
                if (entry == null)
                    entry = createFileEntry(path, e);
                list.add(entry);
            }
        }
        return list;
    }

    public IFileEntry getFileEntry(String path) {
        if (path == null)
            return null;
        IFileEntry entry = findEntry(path);
        if (entry == null) {
            while (path.startsWith("/")) { //$NON-NLS-1$
                path = path.substring(1, path.length());
                entry = findEntry(path);
                if (entry != null)
                    break;
            }
        }
        if (entry == null) {
            entry = findEntry("/" + path); //$NON-NLS-1$
        }
        return entry;
    }

    private IFileEntry findEntry(String path) {
        IFileEntry entry = null;
        if (entries != null)
            entry = entries.get(path);
        if (entry == null) {
            Element e = findEntryElementByPath(path);
            if (e != null)
                entry = createFileEntry(path, e);
        }
        return entry;
    }

    private Element findEntryElementByPath(String path) {
        Iterator<Element> it = DOMUtils.childElementIterByTag(
                getManifestElement(), TAG_FILE_ENTRY);
        while (it.hasNext()) {
            Element e = it.next();
            if (path.equals(e.getAttribute(ATTR_FULL_PATH)))
                return e;
        }
        return null;
    }

    public IFileEntry createFileEntry(String path) {
        return createFileEntry(path, ""); //$NON-NLS-1$
    }

    public IFileEntry createFileEntry(String path, String mediaType) {
        IFileEntry entry = getFileEntry(path);
        if (entry != null)
            return entry;

        String parent = InternalDOMUtils.getParentPath(path);
        if (parent != null) {
            createFileEntry(parent);
        }
        Element e = implementation.createElement(TAG_FILE_ENTRY);
        e.setAttribute(ATTR_FULL_PATH, path);
        e.setAttribute(ATTR_MEDIA_TYPE, mediaType);

        return createFileEntry(path, e);
    }

    private IFileEntry createFileEntry(String path, Element entryElement) {
        IFileEntry entry = new FileEntryImpl(entryElement, this);
        if (entries == null)
            entries = new TreeMap<String, IFileEntry>(ENTRY_COMPARATOR);
        entries.put(path, entry);
        return entry;
    }

//    public void insertElement(IFileEntry entry) {
//        Element e = (Element) entry.getAdapter(Element.class);
//
//        Element ed = DOMUtils.ensureChildElement(e, "encryption-data");
//        ed.setAttribute("data1", "123");
//
//    }

    protected void insertFileEntry(IFileEntry entry) {
        Element e = (Element) entry.getAdapter(Element.class);
        if (e != null) {
            insertFileEntryImpl(e);
        }
    }

    protected void removeFileEntry(IFileEntry entry) {
        Element e = (Element) entry.getAdapter(Element.class);
        if (e != null) {
            Element m = getManifestElement();
            if (m == e.getParentNode())
                m.removeChild(e);
        }
    }

    private void insertFileEntryImpl(Element entryElement) {
        Element e = findInsertLocation(entryElement);
        if (e != null) {
            getManifestElement().insertBefore(entryElement, e);
        } else {
            getManifestElement().appendChild(entryElement);
        }
    }

    private Element findInsertLocation(Element entryElement) {
        if (entryElement.hasAttribute(ATTR_FULL_PATH))
            return findInsertLocation(entryElement, entryElement
                    .getAttribute(ATTR_FULL_PATH));
        return null;
    }

    private Element findInsertLocation(Element entryElement, String path) {
        Iterator<Element> it = DOMUtils.childElementIterByTag(
                getManifestElement(), TAG_FILE_ENTRY);
        while (it.hasNext()) {
            Element e = it.next();
            if (e != entryElement && e.hasAttribute(ATTR_FULL_PATH)) {
                String p = e.getAttribute(ATTR_FULL_PATH);
                if (p != null && path.compareToIgnoreCase(p) < 0) {
                    return e;
                }
            }
        }
        return null;
    }

    public IFileEntry createAttachmentFromFilePath(String sourcePath)
            throws IOException {
        return createAttachmentFromFilePath(sourcePath, null);
    }

    public IFileEntry createAttachmentFromFilePath(String sourcePath,
            String mediaType) throws IOException {
        if (sourcePath == null)
            throw new IllegalArgumentException("Path is null!"); //$NON-NLS-1$
        File file = new File(sourcePath);
        if (!file.exists())
            throw new FileNotFoundException("Source path does not exists."); //$NON-NLS-1$

        if (file.isFile()) {
            IFileEntry entry = createAttachmentFromStream(new FileInputStream(
                    sourcePath), sourcePath, mediaType);
            if (entry != null) {
                entry.setTime(file.lastModified());
            }
            return entry;
        }

        if (file.isDirectory()) {
            String fileName = file.getName();
            String path = makeAttachmentPath(fileName, true);
            if (mediaType == null)
                mediaType = FileUtils.getMediaType(fileName);
            IFileEntry root = createFileEntry(path, mediaType);
            if (root != null) {
                importDirectory(path, file);
            }
            return root;
        }

        throw new IllegalArgumentException(
                "Unknown file type (neither a file nor a directory)"); //$NON-NLS-1$
    }

    protected void importDirectory(String parentPath, File dir)
            throws IOException {
        for (String sub : dir.list()) {
            File f = new File(dir, sub);
            if (f.isFile()) {
                String path = parentPath == null ? sub : parentPath + sub;
                String mediaType = FileUtils.getMediaType(sub);
                IFileEntry e = createFileEntry(path, mediaType);
                if (e != null) {
                    OutputStream os = e.getOutputStream();
                    if (os != null) {
                        FileUtils.transfer(new FileInputStream(f), os);
                    }
                    e.setTime(f.lastModified());
                }
            } else if (f.isDirectory()) {
                String path = parentPath == null ? sub + "/" : //$NON-NLS-1$
                        parentPath + sub + "/"; //$NON-NLS-1$
                importDirectory(path, f);
            }
        }
    }

    public IFileEntry createAttachmentFromStream(InputStream stream,
            String sourceName) throws IOException {
        return createAttachmentFromStream(stream, sourceName, null);
    }

    public IFileEntry createAttachmentFromStream(InputStream stream,
            String sourceName, String mediaType) throws IOException {
        if (sourceName == null || stream == null)
            return null;

        String path = makeAttachmentPath(sourceName, false);
        if (mediaType == null)
            mediaType = FileUtils.getMediaType(sourceName);
        IFileEntry entry = createFileEntry(path, mediaType);
        if (entry != null) {
            OutputStream os = entry.getOutputStream();
            if (os == null)
                throw new IOException();
            FileUtils.transfer(stream, os);
        }
        return entry;
    }

    public IFileEntry cloneEntry(IFileEntry sourceEntry, String targetPath)
            throws IOException {
        if (sourceEntry == null || targetPath == null)
            return null;

        IFileEntry existingEntry = getFileEntry(targetPath);
        if (existingEntry != null) {
            return null;
        }

        if (sourceEntry.isDirectory()) {
            if (!targetPath.endsWith("/")) //$NON-NLS-1$
                targetPath = targetPath + "/"; //$NON-NLS-1$
            importDirectoryEntry(targetPath, sourceEntry);
        } else {
            importFileEntry(targetPath, sourceEntry);
        }
        return getFileEntry(targetPath);
    }

    private void importFileEntry(String path, IFileEntry sourceEntry)
            throws IOException {
        InputStream in = sourceEntry.getInputStream();
        if (in != null) {
            IFileEntry entry = createFileEntry(path, sourceEntry.getMediaType());
            if (entry != null) {
                OutputStream os = entry.getOutputStream();
                if (os != null) {
                    FileUtils.transfer(in, os);
                }
            }
            entry.setTime(sourceEntry.getTime());
        }
    }

    private void importDirectoryEntry(String parentPath, IFileEntry sourceEntry)
            throws IOException {
        String sourceParentPath = getParentPath(sourceEntry.getPath());
        for (IFileEntry sourceSubEntry : sourceEntry.getSubEntries()) {
            String sourceSubPath = sourceSubEntry.getPath();
            if (sourceSubPath != null) {
                String subPath = sourceSubPath.substring(sourceParentPath
                        .length());
                if (parentPath != null) {
                    subPath = parentPath + subPath;
                }
                if (!sourceSubEntry.isDirectory()) {
                    importFileEntry(subPath, sourceSubEntry);
                }
            }
        }
        if (getFileEntry(parentPath) == null) {
            createFileEntry(parentPath, sourceEntry.getMediaType());
        }
    }

    public IFileEntry cloneEntryAsAttachment(IFileEntry sourceEntry)
            throws IOException {
        String sourcePath = sourceEntry.getPath();
        String path = makeAttachmentPath(sourcePath, sourceEntry.isDirectory());
        return cloneEntry(sourceEntry, path);
    }

    public String makeAttachmentPath(String source) {
        return makeAttachmentPath(source, false);
    }

    public String makeAttachmentPath(String source, boolean directory) {
        String path = "attachments/" + Core.getIdFactory().createId() //$NON-NLS-1$
                + FileUtils.getExtension(source);
        if (directory)
            path += "/"; //$NON-NLS-1$
        return path;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.xmind.core.IManifest#getEncryptionData(java.lang.String)
     */
    public IEncryptionData getEncryptionData(String entryPath) {
        IFileEntry entry = getFileEntry(entryPath);
        if (entry != null)
            return entry.getEncryptionData();
        return null;
    }

}