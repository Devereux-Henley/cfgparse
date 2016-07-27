(ns cfgparse.core
 (:require [dk.ative.docjure.spreadsheet :as xl]
           [clojure.java.io :as io]
           [clojure.pprint :refer [pprint]]
           [clojure.string :refer [split starts-with? blank? trim]])
 (:gen-class))

(defn export
  "exports input data to a .xlsx file"
  [outputdata]
  (let [wb (xl/create-workbook "main"
                            outputdata)]
    (xl/save-workbook! "output.xlsx" wb)))

(defn chunkfile 
  "Chunks a file into a map of definitions ordered by number"
  [lines accumulator current-count]
  (if-let [line (first lines)]
    (let [trimmedline (trim line)]
      (if (or (starts-with? trimmedline "define") (starts-with? trimmedline "#") (blank? trimmedline))
        (chunkfile (rest lines) accumulator current-count)
        (if (starts-with? trimmedline "}")
          (chunkfile (rest lines) (assoc accumulator (inc current-count) {}) (inc current-count))
          (let [bits (split trimmedline #" " 2)
                stringbits (map str bits)
                linekey (keyword (first stringbits))
                linevalue (trim (last stringbits))]
            (chunkfile (rest lines) (assoc-in accumulator [current-count linekey] linevalue) current-count)))))
    accumulator))

(defn chunkfile-wrap
  "Wraps chunkfile by opening input file and initializing map at a specified count"
  [current-count filename]
  (with-open [rdr (io/reader filename)]
    (let [file (line-seq rdr)]
      (chunkfile (rest file) {current-count {}} current-count))))
        

(defn readin
  "takes a list of filenames and reads the files into a map"
  [filenames]
  (loop [file (first filenames)
         files (rest filenames)
         iteration 0
         acc {}]
    (if file
      (->>  file
           (chunkfile-wrap (count acc))
           (merge acc)
           (recur (first files) (rest files) (inc iteration)))
      acc)))
        
(defn create-entry
  [headers arr input-chunk]
  (for [header headers]
    (if-let [chunkval ((keyword header) input-chunk)]
      (conj (get arr (.indexOf headers header)) chunkval)
      (conj (get arr (.indexOf headers header)) ""))))

(defn columns-to-rows
  [& colls]
  (partition (count colls) (apply interleave colls)))

(defn clean-row-entries
  [rows]
  (apply filter #(not (empty? %)) rows))

(defn maptoarr
  [input-map headers]
  (let [arr (vec (for [header headers] [header]))
        input-count (count input-map)]
    (loop [cnt 0
           output-array arr]
      (if (> cnt input-count)
        output-array
        (let [current-chunk (get input-map cnt)
              new-entry-arr (vec (create-entry headers output-array current-chunk))
              incremented-count (inc cnt)]
          (recur incremented-count new-entry-arr)))))) ;;need output-array logic for recur

(defn- main
  [& args]
  (with-open [rdr (io/reader "conf.txt")]
    (doseq [line (line-seq rdr)]
      (let [params (split line #":")
            filenames (split (get params 0) #",")
            headers (split (get params 1) #",")
            title (get params 2)]
        (readin filenames)))))



(pprint (clean-row-entries (columns-to-rows (maptoarr (readin ["checkcommands.cfg"]) ["command_line" "command_name"]))))
