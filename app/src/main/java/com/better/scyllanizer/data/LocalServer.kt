package com.better.scyllanizer.data

import fi.iki.elonen.NanoHTTPD
import java.io.File

class LocalServer(port: Int = 8080, private val rootFolder: File) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
                val tmpFilePath = files["file"]
                
                if (tmpFilePath != null) {
                    val tmpFile = File(tmpFilePath)
                    
                    // FIXED: Read the original filename from the "file" parameter key
                    val originalFileName = session.parameters["file"]?.firstOrNull()
                    
                    val fileName = if (!originalFileName.isNullOrBlank()) {
                        originalFileName
                    } else {
                        // Fallback just in case
                        "upload_${System.currentTimeMillis()}.bin"
                    }
                    
                    val destFile = File(rootFolder, fileName)
                    tmpFile.copyTo(destFile, overwrite = true)
                    
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "Uploaded $fileName")
                }
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message)
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>ScyllaNizer Drop</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #09090B; color: #F4F4F5; text-align: center; padding: 20px; margin: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 80vh; }
                    h1 { color: #06B6D4; margin-bottom: 5px; }
                    p { color: #A1A1AA; margin-top: 0; margin-bottom: 30px; }
                    
                    .drop-zone { 
                        border: 2px dashed #06B6D4; 
                        border-radius: 20px; 
                        padding: 50px 20px; 
                        width: 90%; 
                        max-width: 500px; 
                        transition: all 0.2s ease;
                        cursor: pointer;
                        background: #18181B;
                    }
                    .drop-zone:hover, .drop-zone.dragover {
                        background: rgba(6, 182, 212, 0.1);
                        border-color: #F4F4F5;
                        transform: scale(1.02);
                    }
                    
                    input[type="file"] { display: none; }
                    
                    .icon { font-size: 40px; margin-bottom: 15px; }
                    .btn { 
                        background: #06B6D4; 
                        color: #000; 
                        padding: 12px 25px; 
                        border-radius: 10px; 
                        font-weight: bold; 
                        display: inline-block;
                        margin-top: 15px;
                    }
                    .mobile-text { display: none; }
                    
                    #status { margin-top: 20px; color: #06B6D4; font-weight: bold; min-height: 20px; }
                    
                    @media (max-width: 768px) {
                        .desktop-text { display: none; }
                        .mobile-text { display: block; }
                    }
                </style>
            </head>
            <body>
                <h1>ScyllaNizer Drop</h1>
                <p>Send files directly to your phone's folder.</p>
                
                <div class="drop-zone" id="dropZone" onclick="document.getElementById('fileInput').click()">
                    <div class="icon">📁</div>
                    
                    <div class="desktop-text">
                        <h3>Drag & Drop files here</h3>
                        <span style="color:#A1A1AA;">or</span>
                    </div>
                    
                    <div class="mobile-text">
                        <h3>Tap to select files</h3>
                    </div>

                    <span class="btn">Browse Files</span>
                    
                    <form id="uploadForm" enctype="multipart/form-data" method="post">
                        <input type="file" id="fileInput" name="file" multiple onchange="uploadFiles(this.files)">
                    </form>
                    
                    <div id="status"></div>
                </div>
                
                <script>
                    const dropZone = document.getElementById('dropZone');
                    const status = document.getElementById('status');

                    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
                        dropZone.addEventListener(eventName, preventDefaults, false);
                        document.body.addEventListener(eventName, preventDefaults, false);
                    });

                    function preventDefaults (e) {
                        e.preventDefault();
                        e.stopPropagation();
                    }

                    ['dragenter', 'dragover'].forEach(eventName => {
                        dropZone.addEventListener(eventName, highlight, false);
                    });

                    ['dragleave', 'drop'].forEach(eventName => {
                        dropZone.addEventListener(eventName, unhighlight, false);
                    });

                    function highlight(e) { dropZone.classList.add('dragover'); }
                    function unhighlight(e) { dropZone.classList.remove('dragover'); }

                    dropZone.addEventListener('drop', handleDrop, false);

                    function handleDrop(e) {
                        let dt = e.dataTransfer;
                        let files = dt.files;
                        uploadFiles(files);
                    }

                    function uploadFiles(files) {
                        if(files.length === 0) return;
                        status.innerText = "Uploading...";
                        status.style.color = "#06B6D4";
                        let uploaded = 0;
                        
                        for (let i = 0; i < files.length; i++) {
                            const formData = new FormData();
                            formData.append('file', files[i]);
                            
                            fetch('/', { method: 'POST', body: formData })
                            .then(() => {
                                uploaded++;
                                status.innerText = "Uploaded " + uploaded + " / " + files.length + " files!";
                                if(uploaded === files.length) {
                                    status.innerText = "✅ All files uploaded successfully!";
                                    status.style.color = "#22c55e";
                                    setTimeout(() => { status.innerText = ""; }, 3000);
                                }
                            }).catch(() => {
                                status.innerText = "❌ Upload failed. Check connection.";
                                status.style.color = "#ef4444";
                            });
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
