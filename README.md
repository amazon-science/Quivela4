# Quivela4

Quivela4 is a proof checker for universally composable (UC) security proofs of cryptographic protocols. 

## Disclaimer

Quivela4 is a research project with the purpose of exploring techniques for machine-checked security proofs. The design and implementation of this tool may have flaws that cause it to make incorrect security claims. Use at your own risk, and please submit issues containing any soundness bugs.  

## Dependencies 

Quivela4 has some dependencies that must be installed manually before building/running. 

1. Follow instructions in the build_deps [README](build_deps/README.md) file to install build dependencies. 
2. Install [Boogie](https://github.com/boogie-org/boogie). The simple solution is to put the `boogie` executable on your path before running Quivela4. You can also specify the path to Boogie when you run Quivela4. 

## Building and Running

To build (after setting up build dependencies), execute `mvn install`. This will produce a `target` folder containing a `Quivela4.jar` file.

To run, execute `java -jar target/Quivela4.jar <filename>`. For example, to check the FIFO channel proof, execute `java -jar target/Quivela4.jar examples/fifo.qvl`. Quivela4 looks in the working directory for other files to include---the command above should be executed in the top-level directory of the repository so the `stdlib` files can be included.  

Quivela4 is a work in progress, and the language is not documented, yet. Currently the best way to learn how to use this tool is to look at the existing examples. 

## License

This project is licensed under the [Apache-2.0 License](LICENSE). See [Contributing](CONTRIBUTING.md) for information about contributions to this repository.
