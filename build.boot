(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojure "1.8.0" :scope "provided"]
                  [dk.ative/docjure "1.10.0"]])

(task-options!
  pom {:project 'cfgparse
       :version "0.2.0"
       :description "A program to transform .cfg files for nagios configuration to a readable excel format"
       :url "https://github.com/Devereux-Henley/cfgparse"
       :license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}}
  aot {:namespace '#{cfgparse.core}}
  jar {:main 'cfgparse.core})

(deftask build
  []
  (comp
    (pom)
    (aot)
    (uber)
    (jar)
    (target)))

(deftask run
  "run cfgparse w/o creating an uberjar. Require .edn config file."
  [c config VAL str "input edn configuration file (e.g. config.edn)"]
  (require '[cfgparse.core])
  (if config 
    ((resolve 'cfgparse.core/-main) config)
    (do 
      (boot.util/fail "-c/--config option is required\n")
      (*usage*))))
