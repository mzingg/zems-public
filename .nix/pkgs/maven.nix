{
  stdenv,
  fetchurl,
  makeWrapper,
  jdk25,
  lib,
}:

stdenv.mkDerivation rec {
  pname = "apache-maven";
  version = "4.0.0-rc-5";

  src = fetchurl {
    url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${version}/apache-maven-${version}-bin.tar.gz";
    sha256 = "18bcn66lwb1xlwsw5cxw42hj0fpap5783lgywxv1q11xkp4sbrpc";
  };

  nativeBuildInputs = [ makeWrapper ];

  installPhase = ''
    mkdir -p $out
    cp -r * $out/

    for cmd in mvn mvnDebug mvnyjp; do
      wrapProgram $out/bin/$cmd \
        --set JAVA_HOME "${jdk25}" \
        --set M2_HOME "$out"
    done
  '';

  meta = {
    description = "Apache Maven 4 build automation tool";
    homepage = "https://maven.apache.org/";
    license = lib.licenses.asl20;
  };
}
