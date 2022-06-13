(ns cline.main)

(require '[babashka.cli :as cli]
         '[babashka.fs :as fs]
         '[clojure.string :as str])

(def version "v0.0.1")

(defn print-version [{:keys [opts]}]
  (println "cline" version))

(defn print-help [{:keys [opts]}]
  (println (str/triml (str
"cline cli version " version "

This help prompt under construction."))))

(def deps-template
  (str/triml "
{:deps {}
 :aliases {}}
"))

(def bb-template
  (str/triml "
{:deps {}
 :tasks {}}
"))

(def extensions
  {"clojure" ".clj"
   "clojurescript" ".cljs"
   "clojuredart" ".cljd"})

(def type-names
  {"clojure" "Clojure"
   "clojurescript" "ClojureScript"
   "clojuredart" "ClojureDart"})

(defn ensure-main [opts]
  (let [proj-path (:project-name opts)
        extension (extensions (str/lower-case (:type opts)))
        target (str (fs/path proj-path "src" proj-path (str "main" extension)))]
    (when-not (fs/exists? target)
      (spit target (str "(ns " proj-path ".main)")))))

(defn ensure-src-namespace [opts]
  (let [proj-path (:project-name opts)
        target (str (fs/path proj-path "src" proj-path))]
    (when-not (fs/exists? target)
      (fs/create-dirs target))))

(defn ensure-deps-file [opts]
  (let [proj-path (:project-name opts)
        target (str (fs/path proj-path (:deps-file opts)))]
    (when-not (fs/exists? target)
      (spit target (if (= "bb.edn" target)
                     bb-template
                     deps-template)))))

(defn ensure-project-folder [opts]
  (let [target (:project-name opts)]
    (when-not (fs/exists? target)
      (fs/create-dir target))))

(defn new-project [{:keys [opts]}]
  (ensure-project-folder opts)
  (ensure-deps-file opts)
  (ensure-src-namespace opts)
  (ensure-main opts)
  (println (str "Generating a " 
                (type-names (str/lower-case (:type opts)))
                " project called " 
                (:project-name opts) ".") ))

(defn -main [& _args]
  (cli/dispatch
   [{:cmds ["new"] :fn new-project :cmds-opts [:project-name]}
    {:cmds ["check-args"] :fn identity :cmds-opts [:project-name]}
    {:cmds [] :fn (fn [{:keys [opts] :as m}]
                    (if (:version opts)
                      (print-version m)
                      (print-help m)))}]
   *command-line-args*
   {:coerce {:deps-deploy parse-boolean
             :as symbol
             :alias keyword
             :limit parse-long}
    :exec-args {:deps-file "deps.edn"
                :license "MIT"
                :type "clojure"}}))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))