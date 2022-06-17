#!/usr/bin/env bb

;; Declare namespace
(ns build)

;; Require dependencies
(require '[babashka.fs :as fs]
         '[babashka.process :refer [process]])

;; Ensure "out" directory
(when-not (fs/exists? "out")
  (fs/create-dir "out"))

;; Run `bb uberscript` to package source at src/cline/main.clj
(let [cmd ["bb" "uberscript" "./out/cline-intermediate"
           "-f" "src/cline/main.clj"]]
  (process cmd))

;; Create new file and add bb shebang
(spit "./out/cline" "#!/usr/bin/env bb\n\n")

;; Append uberscript to script file
(spit "./out/cline" (slurp "./out/cline-intermediate") :append true)

;; Delete intermediate build file
(fs/delete-if-exists "./out/cline-intermediate")

;; Change permissions on script file to add execution
(let [cmd ["chmod" "+x" "./out/cline"]]
  (process cmd))

;; Print success message
(println "Rebuild successful")
