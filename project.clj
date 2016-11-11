(defproject cfgparse "0.2.0"
 :description "A program to transform .cfg files for nagios configuration to a readable excel format"
 :url "https://github.com/Devereux-Henley/cfgparse"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]
                [dk.ative/docjure "1.10.0"]]
 :plugins [[cider/cider-nrepl "0.12.0"]]
 :main cfgparse.core)
