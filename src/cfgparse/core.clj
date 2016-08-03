(ns cfgparse.core
 (:require [dk.ative.docjure.spreadsheet :as xl]
           [clojure.java.io :as io]
           [clojure.string :refer [split starts-with? blank? trim]]
           [clojure.edn :as edn])
 (:gen-class))

;;Name of excel output file
(def outputfile
  "output.xlsx")

(defn export
  "exports input data to a .xlsx file
  [['foo' 'bar']] 'sheet' => nil"
  [title outputdata]
  (if (.exists (io/as-file outputfile))
    (let [wb (xl/load-workbook outputfile)]
      (xl/add-rows! (xl/add-sheet! wb title) outputdata)
      (xl/save-workbook! outputfile wb))
    (let [wb (xl/create-workbook title
                                 outputdata)]
      (xl/save-workbook! outputfile wb))))

(defn chunkfile 
  "Chunks a file into a map of definitions ordered by number
  '('foo\tbar') {} 0 => {0 {:foo 'bar'} 1 {}}"
  [lines accumulator current-count]
  (if-let [line (first lines)]
    (let [trimmedline (trim line)]
      (if (or (starts-with? trimmedline "define") (starts-with? trimmedline "#") (blank? trimmedline))
        (chunkfile (rest lines) accumulator current-count)
        (if (starts-with? trimmedline "}")
          (chunkfile (rest lines) (assoc accumulator (inc current-count) {}) (inc current-count))
          (let [bits (split trimmedline #"\s+" 2)
                linekey (keyword (first bits))
                linevalue (trim (last bits))]
            (chunkfile (rest lines) (assoc-in accumulator [current-count linekey] linevalue) current-count)))))
    accumulator))

(defn chunkfile-wrap
  "Wraps chunkfile by opening input file and initializing map at a specified count
  'foo.cfg' => {0 {:command_name 'wiz'}}"
  [current-count filename]
  (with-open [rdr (io/reader filename)]
    (let [file (line-seq rdr)]
      (chunkfile (rest file) {current-count {}} current-count))))
        

(defn readin
  "takes a list of filenames and reads the files into a map.
  ['foo.cfg' 'bar.cfg'] => {0 {:command_name 'wiz'} 1 {:command_name 'woz'}}"
  [filenames]
  (loop [file (first filenames)
         files (rest filenames)
         acc {}]
    (if file
      (->>  file
           (chunkfile-wrap (count acc))
           (merge acc)
           (recur (first files) (rest files)))
      acc)))    

(defn columns-to-rows
  "Inverts a 2d vector.
  [['foo' 'bar'] ['baz' 'boo']] -> [['foo' 'baz'] ['bar' 'boo']]"
  [colls]
  (partition (count colls) (apply interleave colls)))

(defn create-entry
  "Updates array with new entry from input-chunk that matches headers
  ['cat' 'dog'] [['foo'] ['bar']] {:cat 'baz'} => [['foo' 'baz'] ['bar' '']]"
  [headers arr input-chunk]
  (if (empty? input-chunk)
    arr
    (mapv
      conj
      arr
      (vec (map #(if-let [hval ((keyword %) input-chunk)] hval "") headers)))))

(defn split-entries
  "Creates duplicate rows based on comma split.
  '('foo' 'bar,baz') => [['foo' 'bar] ['foo' 'baz']]"
  [list-input idx]
  (let [arr (vec list-input)]
    (loop [split-list (split (get arr idx) #",")
           acc []]
      (if-let [split-item (first split-list)]
        (recur (rest split-list) (conj acc (assoc arr idx split-item)))
        acc))))

(defn maptoarr
  "Converts file map to 2d vector
  {0 {:foo 'bar' :cat 'dog'} 1 {:foo 'baz' :cat 'bat'}} => [['bar' 'baz'] ['dog' 'bat']]"
  [headers input-map]
  (let [arr (vec (map #(vector %) headers))
        input-count (count input-map)]
    (loop [cnt 0
           output-array arr]
      (if (> cnt input-count)
        output-array
        (let [current-chunk (get input-map cnt)
              new-arr (create-entry headers output-array current-chunk)
              incremented-count (inc cnt)]
          (recur incremented-count new-arr))))))

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
              filtered-dirs (remove #(.isDirectory %) dir-seq)
              dir-files (map #(.getPath %) filtered-dirs)]
          (recur (concat file-acc dir-files) (rest remain-dirs)))
        file-acc))))
  
(defn -main
  "Takes in a path to a config file, parses it, processes the files pointed to in the
  config, and exports the definitions to a .xlsx file."
  [& args]
  (io/delete-file outputfile true)
  (if-let [conf-file-path (nth args 0 nil)]
    (if (.exists (io/as-file conf-file-path))
      (let [sheets (read-config conf-file-path)]
        (doseq [sheet sheets]
          (let [filenames (:files sheet)
                headers (:headers sheet)
                title (:title sheet)
                dirs (:dirs sheet)
                splitfield (:split-item sheet)]
            (if splitfield
              (->> (build-file-arr filenames dirs)
                  (readin)
                  (maptoarr headers)
                  (columns-to-rows)
                  (map #(split-entries % (.indexOf headers splitfield)))
                  (mapcat identity)
                  (export title))
              (->> (build-file-arr filenames dirs)
                  (readin)
                  (maptoarr headers)
                  (columns-to-rows)
                  (export title))))))
      (println (str conf-file-path " could not be found.")))
    (println "cfgparse requires relative configuration file path as first argument."))) 
