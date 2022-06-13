(ns cline.main)

(require '[babashka.cli :as cli]
         '[babashka.fs :as fs]
         '[clojure.string :as str]
         '[babashka.curl :as curl]
         '[cheshire.core :as cheshire]
         '[babashka.process :refer [sh process]])

(import java.net.URLEncoder)
(import java.time.YearMonth)

(def version "v0.0.1")

(def git-name (-> (process '[git config user.name]) :out slurp str/trim-newline))

(defn print-version [{:keys [opts]}]
  (println "cline" version))

(defn print-help [{:keys [opts]}]
  (println (str/triml (str
"cline cli version " version "

This help prompt under construction."))))

(defn print-license-help [{:keys [opts]}]
  (println (str/triml (str
"cline cli version " version "

This help prompt specific to the \"license\" subcommand is under construction."))))

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

(defn ensure-main [{:keys [opts]}]
  (let [proj-path (:project-name opts)
        extension (extensions (str/lower-case (:type opts)))
        target (str (fs/path proj-path "src" proj-path (str "main" extension)))]
    (when-not (fs/exists? target)
      (spit target (str "(ns " proj-path ".main)")))))

(defn ensure-src-namespace [{:keys [opts]}]
  (let [proj-path (:project-name opts)
        target (str (fs/path proj-path "src" proj-path))]
    (when-not (fs/exists? target)
      (fs/create-dirs target))))

(defn ensure-deps-file [{:keys [opts]}]
  (let [proj-path (:project-name opts)
        target (str (fs/path proj-path (:deps-file opts)))]
    (when-not (fs/exists? target)
      (spit target (if (= "bb.edn" target)
                     bb-template
                     deps-template)))))

(defn ensure-project-folder [{:keys [opts]}]
  (let [target (:project-name opts)]
    (when-not (fs/exists? target)
      (fs/create-dir target))))

(def licenses-api-url "https://api.github.com/licenses")

(def windows? (str/includes? (System/getProperty "os.name") "Windows"))

(def curl-opts
  {:throw false
   :compressed (not windows?)})

(defn curl-get-json [url]
  (-> (curl/get url curl-opts)
      :body
      (cheshire/parse-string true)))

(defn license-search [{:keys [opts]}]
  (let [search-term (:search-term opts)
        license-vec (->> (str licenses-api-url "?per_page=50")
                         curl-get-json
                         (map #(select-keys % [:key :name])))
        search-results (if search-term
                         (filter #(str/includes?
                                   (str/lower-case (:name %))
                                   (str/lower-case search-term))
                                 license-vec)
                         license-vec)]
    (if (empty? search-results)
      (binding [*out* *err*]
        (println "No licenses found")
        (System/exit 1))
      (doseq [result search-results]
        (println :license (:key result) :name (pr-str (:name result)))))))

(defn url-encode [s] (URLEncoder/encode s "UTF-8"))

(defn license-to-file [{:keys [opts]}]
  (let [license-key (:license opts)
        project-path (:project-name opts)
        output-file (str (fs/path project-path (or (:file opts) "LICENSE")))
        {:keys [message name body]} (->> license-key url-encode
                                         (str licenses-api-url "/")
                                         curl-get-json)]
    (cond
      (not license-key) (throw (ex-info "No license key provided." {}))
      (= message "Not Found") (throw (ex-info (format "License '%s' not found." license-key)
                                              {:license license-key}))
      (not body) (throw (ex-info (format "License '%s' has no body text." (or name license-key))
                                 {:license license-key}))
      :else (do (when (fs/exists? output-file)
                  (println "Found a LICENSE file!")
                  (println "Appending new license language to the end."))
                (spit output-file body :append true)))))

(defn license-add [opts]
  (try
    (license-to-file opts)
    (catch Exception e
      (binding [*out* *err*]
        (println (ex-message e))
        (System/exit 1)))))

(defn license-update [{:keys [opts]}]
  (let [proj-path (:project-name opts)
        target (str (fs/path proj-path (or (:file opts) "LICENSE")))
        in-file (slurp target)
        out-file (str/replace in-file "[year] [fullname]" 
                              (str (.getYear (.now YearMonth)) " " git-name))]
    (spit target out-file)))

(defn new-project [opts]
  (ensure-project-folder opts)
  (ensure-deps-file opts)
  (ensure-src-namespace opts)
  (ensure-main opts)
  (license-add opts)
  (when git-name (license-update opts))
  (let [opts (:opts opts)]
    (println (str "Generating a "
                  (type-names (str/lower-case (:type opts)))
                  " project called "
                  (:project-name opts) "."))))

(defn -main [& _args]
  (cli/dispatch
   [{:cmds ["new"] :fn new-project :cmds-opts [:project-name]}
    {:cmds ["check-args"] :fn identity :cmds-opts [:project-name]}
    {:cmds ["license" "list"] :fn license-search :cmds-opts [:search-term]}
    {:cmds ["license" "search"]  :fn license-search :cmds-opts [:search-term]}
    {:cmds ["license" "add"] :fn license-add :cmds-opts [:license]}
    {:cmds ["license"] :fn print-license-help}
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
                :license "mit"
                :type "clojure"}}))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))