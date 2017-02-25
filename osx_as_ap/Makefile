all: osx_as_ap

osx_as_ap: osx_as_ap.m
	clang -framework Foundation -framework CoreWLAN -o $@ $<

clean:
	rm -rf osx_as_ap
