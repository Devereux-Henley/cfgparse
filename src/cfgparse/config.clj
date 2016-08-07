(ns cfgparse.config
 (:require [clojure.edn :as edn]
           [clojure.java.io :as io]))
            

(defrecord Sheet-Data
  [title files dirs headers split-item])

(defn parse-sheet-data
  "Convert config file kv-pair to Sheet-Data record"
  [pair]
  (let [title (name (first pair))
        conf-map (second pair)
        headers (:headers conf-map)
        files (:files conf-map)
        dirs (:dirs conf-map)
        split-item (:split conf-map)]
    (->Sheet-Data title files dirs headers split-item)))

(defn read-config
  "Read in config file and convert to list of Sheet-Data records"
  [conf-file-path]
  (with-open [r (java.io.PushbackReader. (io/reader conf-file-path))]
    (let [edn-seq (repeatedly (partial edn/read {:eof :eof} r))
          conf-seq (partition 2 (take-while (partial not= :eof) edn-seq))]
     (doall (map parse-sheet-data conf-seq)))))

(defn build-file-arr
  [files dirs]
  (loop [file-acc files
         remain-dirs dirs]
    (let [dir (first remain-dirs)]
      (if dir
        (let [dir-seq (file-seq (io/file dir))
              filter-dirs (remove #(.isDirectory %) dir-seq)
              dir-files (map #(.getPath %) filter-dirs)]
          (recur (concat file-acc dir-files) (rest remain-dirs)))
        file-acc))))