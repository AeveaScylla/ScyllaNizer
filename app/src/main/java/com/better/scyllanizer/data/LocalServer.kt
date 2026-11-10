package com.better.scyllanizer.data

import fi.iki.elonen.NanoHTTPD
import java.io.File

class LocalServer(
    port: Int = 8080,
    private val rootFolder: File,
    private val initialDestination: String = ""
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/api/folders" -> listFolders(session)
                session.method == Method.POST && session.uri == "/upload" -> receiveUpload(session)
                session.method == Method.GET -> page()
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "Server error")
        }
    }

    private fun root(): File {
        val root = rootFolder.canonicalFile
        if (!root.exists() && !root.mkdirs()) throw IllegalStateException("Upload folder is unavailable")
        if (!root.isDirectory) throw IllegalStateException("Upload location is not a folder")
        return root
    }

    private fun listFolders(session: IHTTPSession): Response {
        val root = root()
        val requested = session.parameters["path"]?.firstOrNull().orEmpty()
        val folder = resolveInside(root, requested, allowEmpty = true)
        if (!folder.exists() || !folder.isDirectory) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Folder not found\"}")
        }
        val children = folder.listFiles()?.filter { it.isDirectory && !it.isHidden }
            ?.sortedBy { it.name.lowercase() }.orEmpty()
        val entries = children.mapNotNull { child ->
            val canonical = child.canonicalFile
            if (!canonical.path.startsWith(root.path + File.separator)) null
            else "{\"name\":\"${jsonEscape(child.name)}\",\"path\":\"${jsonEscape(relative(root, child))}\"}"
        }
        val body = "{\"current\":\"${jsonEscape(relative(root, folder))}\",\"folders\":[${entries.joinToString(",") }]}"
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)
    }

    private fun receiveUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val tempPath = files["file"]
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No file received")
        val original = session.parameters["relativePath"]?.firstOrNull()
            ?: session.parameters["file"]?.firstOrNull()
            ?: "upload_${System.currentTimeMillis()}.bin"
        val destination = session.parameters["destination"]?.firstOrNull().orEmpty()
        val root = root()
        val relativePath = safeRelativePath(original)
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid file path")
        val destinationFolder = resolveInside(root, destination, allowEmpty = true)
        if (!destinationFolder.exists() && !destinationFolder.mkdirs()) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Cannot create destination folder")
        }
        if (!destinationFolder.isDirectory) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Destination is not a folder")
        }

        val segments = relativePath.split('/').filter { it.isNotBlank() }
        val relativeParent = segments.dropLast(1).joinToString("/")
        val targetFolder = if (relativeParent.isEmpty()) destinationFolder
            else resolveInside(destinationFolder, relativeParent, allowEmpty = true)
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Cannot create upload subfolder")
        }
        if (!targetFolder.isDirectory) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid upload subfolder")
        }

        val requestedName = segments.last()
        var target = File(targetFolder, requestedName).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid destination")
        }
        val base = target.nameWithoutExtension
        val ext = target.extension.let { if (it.isBlank()) "" else ".$it" }
        var suffix = 1
        while (target.exists()) {
            target = File(targetFolder, "$base ($suffix)$ext").canonicalFile
            suffix++
        }
        File(tempPath).copyTo(target, overwrite = false)
        return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "Saved ${relative(root, target)}")
    }

    private fun safeRelativePath(value: String): String? {
        val normalized = value.trim().replace('\\', '/')
        if (normalized.isBlank() || normalized.startsWith('/')) return null
        val parts = normalized.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." || it.any { ch -> ch.isISOControl() } }) return null
        return parts.joinToString("/")
    }

    private fun resolveInside(base: File, path: String, allowEmpty: Boolean): File {
        val safe = if (path.isBlank() && allowEmpty) "" else safeRelativePath(path)
            ?: throw IllegalArgumentException("Invalid folder path")
        val resolved = if (safe.isEmpty()) base.canonicalFile else File(base, safe).canonicalFile
        val canonicalBase = base.canonicalFile
        if (resolved != canonicalBase && !resolved.path.startsWith(canonicalBase.path + File.separator)) {
            throw IllegalArgumentException("Folder path is outside the upload location")
        }
        return resolved
    }

    private fun relative(base: File, child: File): String = child.canonicalPath
        .removePrefix(base.canonicalPath).trimStart(File.separatorChar).replace(File.separatorChar, '/')

    private fun jsonEscape(value: String): String = value.replace("\\", "\\\\")
        .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun page(): Response {
        val html = """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>ScyllaNizer Drop</title><style>
            *{box-sizing:border-box}body{font-family:system-ui,-apple-system,sans-serif;background:#09090b;color:#f4f4f5;margin:0;padding:22px;min-height:100vh;display:flex;justify-content:center;align-items:center}
            main{width:100%;max-width:560px}h1{color:#06b6d4;margin:0 0 5px}p{color:#a1a1aa;margin:0 0 20px}.panel{background:#18181b;border:1px solid #3f3f46;border-radius:16px;padding:18px;margin:14px 0}
            button,.btn{border:0;border-radius:10px;padding:12px 16px;background:#06b6d4;color:#061014;font-weight:700;cursor:pointer;display:inline-block;margin:5px 4px 5px 0}
            button.secondary{background:#3f3f46;color:#f4f4f5}button:disabled{opacity:.5}.path{color:#67e8f9;overflow-wrap:anywhere}.folder{display:block;width:100%;text-align:left;background:#27272a;color:#f4f4f5;margin:6px 0}.drop{border:2px dashed #0891b2;text-align:center;padding:24px;border-radius:14px;margin-top:12px}.muted{color:#a1a1aa;font-size:13px}#status{min-height:24px;margin-top:14px;font-weight:600;overflow-wrap:anywhere}progress{width:100%;height:12px;accent-color:#06b6d4}input{display:none}
            </style></head><body><main><h1>ScyllaNizer Drop</h1><p>Send files or whole folders to your phone.</p>
            <section class="panel"><b>Save to</b><div class="path" id="currentPath">/</div><button class="secondary" id="chooseBtn">Choose destination folder</button><div id="folderBrowser" hidden><button class="secondary" id="upBtn">↑ Up</button><div id="folderList"></div><button class="secondary" id="useFolderBtn">Use this folder</button></div></section>
            <section class="panel"><b>Select what to send</b><div><button id="filesBtn">Choose files</button><button id="folderBtn">Choose whole folder</button></div>
            <div class="drop" id="dropZone">📁<br><b>Drag and drop files here</b><div class="muted">Folder picker preserves subfolders. Multiple files upload as a batch.</div></div>
            <input id="fileInput" type="file" multiple><input id="folderInput" type="file" multiple webkitdirectory directory>
            <progress id="progress" max="100" value="0" hidden></progress><div id="status" role="status"></div></section>
            </main><script>
            const $=id=>document.getElementById(id);let destination='${jsonEscape(initialDestination)}',browsePath='';let busy=false;
            function showPath(){ $('currentPath').textContent='/'+destination; }
            async function loadFolders(path){browsePath=path;const r=await fetch('/api/folders?path='+encodeURIComponent(path));if(!r.ok)throw new Error(await r.text());const data=await r.json();$('folderList').innerHTML='';if(path){const parent=path.split('/').slice(0,-1).join('/');const b=document.createElement('button');b.className='folder';b.textContent='📁 ..';b.onclick=()=>loadFolders(parent);$('folderList').append(b)}
            if(!data.folders.length){const e=document.createElement('div');e.className='muted';e.textContent='No subfolders';$('folderList').append(e)}
            data.folders.forEach(f=>{const b=document.createElement('button');b.className='folder';b.textContent='📁 '+f.name;b.onclick=()=>loadFolders(f.path);$('folderList').append(b)});}
            $('chooseBtn').onclick=async()=>{try{$('folderBrowser').hidden=false;await loadFolders(destination)}catch(e){$('status').textContent='Could not list folders: '+e.message}};
            $('upBtn').onclick=()=>loadFolders(browsePath.split('/').slice(0,-1).join('/'));
            $('useFolderBtn').onclick=()=>{destination=browsePath;showPath();$('folderBrowser').hidden=true};
            $('filesBtn').onclick=()=>$('fileInput').click();$('folderBtn').onclick=()=>$('folderInput').click();
            $('fileInput').onchange=e=>uploadBatch(e.target.files,false);$('folderInput').onchange=e=>uploadBatch(e.target.files,true);
            $('dropZone').ondragover=e=>{e.preventDefault();$('dropZone').style.borderColor='#f4f4f5'};$('dropZone').ondragleave=()=>{$('dropZone').style.borderColor='#0891b2'};
            $('dropZone').ondrop=e=>{e.preventDefault();$('dropZone').style.borderColor='#0891b2';uploadBatch(e.dataTransfer.files,false)};
            function uploadOne(file,relative,index,total){return new Promise((resolve,reject)=>{const form=new FormData();form.append('file',file,file.name);form.append('relativePath',relative);form.append('destination',destination);const xhr=new XMLHttpRequest();xhr.open('POST','/upload');xhr.upload.onprogress=e=>{if(e.lengthComputable){const pct=Math.round(e.loaded/e.total*100);$('status').textContent='Uploading '+relative+' — '+pct+'% ('+(index+1)+'/'+total+')'}};xhr.onload=()=>xhr.status>=200&&xhr.status<300?resolve(xhr.responseText):reject(new Error(xhr.responseText||'HTTP '+xhr.status));xhr.onerror=()=>reject(new Error('Connection lost while uploading '+relative));xhr.send(form)})}
            async function uploadBatch(fileList,isFolder){if(busy||!fileList||!fileList.length)return;busy=true;const files=Array.from(fileList);let saved=0;$('progress').hidden=false;$('progress').value=0;$('status').textContent='Preparing '+files.length+' files…';try{for(let i=0;i<files.length;i++){const rel=isFolder?(files[i].webkitRelativePath||files[i].name):files[i].name;await uploadOne(files[i],rel,i,files.length);saved++;$('progress').value=Math.round(saved/files.length*100);$('status').textContent='Saved '+saved+' / '+files.length+' files'}$('status').textContent='✅ All '+saved+' files saved to /'+destination;$('status').style.color='#4ade80'}catch(e){$('status').textContent='❌ '+e.message+' ('+saved+'/'+files.length+' saved)';$('status').style.color='#f87171'}finally{busy=false;$('fileInput').value='';$('folderInput').value='';}}
            showPath();
            </script></body></html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }
}
