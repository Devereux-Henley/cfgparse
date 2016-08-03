# cfgparse

A Clojure tool to transform groups of cfg files to a readable excel format.

## Usage

A .edn config file must be included as the first argument.

```clojure
;;Key will be the name of the sheet in the output xlsx file.
:first_sheet {:headers ["foo" "bar"]
              :split "bar" ;;optional value that must be split by comma. Must be a header
              :files  ["tmp/test.cfg" "tmp/temp.cfg"] ;;optional, individual files to convert.
              :dirs ["tmp"] } ;;Populates files with every file in the directory and its subdirectories.
              
:other_sheet {:headers ["baz" "boo" "bob"] ;;Number of headers can be varied
              :dirs ["test2"] };;Either dirs or files may be used if both aren't needed.
```

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
