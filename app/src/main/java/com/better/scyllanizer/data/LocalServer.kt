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
                    val fileName = session.parameters["filename"]?.firstOrNull() ?: "upload_${System.currentTimeMillis()}"
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
                    body { font-family: sans-serif; background: #09090B; color: #F4F4F5; text-align: center; padding: 50px; }
                    h1 { color: #06B6D4; }
                    .drop-zone { border: 2px dashed #06B6D4; border-radius: 20px; padding: 50px; margin: 20px auto; max-width: 500px; }
                    input[type="file"] { display: none; }
                    label { background: #06B6D4; color: #000; padding: 15px 30px; border-radius: 10px; cursor: pointer; font-weight: bold; }
                </style>
            </head>
            <body>
                <h1>ScyllaNizer Drop</h1>
                <p>Drop files here to auto-sort them.</p>
                <div class="drop-zone">
                    <form id="uploadForm" enctype="multipart/form-data" method="post">
                        <input type="file" id="fileInput" name="file" multiple onchange="uploadFiles()">
                        <label for="fileInput">Select Files</label>
                    </form>
                    <p id="status" style="margin-top:20px; color:#A1A1AA;"></p>
                </div>
                <script>
                    function uploadFiles() {
                        const files = document.getElementById('fileInput').files;
                        const status = document.getElementById('status');
                        if(files.length === 0) return;
                        status.innerText = "Uploading...";
                        let uploaded = 0;
                        for (let i = 0; i < files.length; i++) {
                            const formData = new FormData();
                            formData.append('file', files[i]);
                            fetch('/', { method: 'POST', body: formData })
                            .then(() => {
                                uploaded++;
                                status.innerText = "Uploaded " + uploaded + " / " + files.length + " files!";
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
