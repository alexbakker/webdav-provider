{ config, pkgs, lib, ... }:

let
  davPath = "/var/lib/webdav";
in {
  services.nginx = {
    enable = true;
    package = pkgs.nginx.override {
      modules = with pkgs.nginxModules; [ dav ];
    };

    virtualHosts."_" = {
      locations = {
        "/" = {
          root = davPath;
          extraConfig = ''
            autoindex on;
            client_max_body_size 1g;

            dav_methods PUT DELETE MKCOL COPY MOVE;
            dav_ext_methods PROPFIND OPTIONS;
          '';
        };
      };
    };
  };

  systemd.services.nginx = {
    serviceConfig = {
      ReadWritePaths = davPath;
    };
  };

  systemd.tmpfiles.rules = [
    "d ${davPath} 0770 nginx nginx -"
  ];

  networking.firewall.allowedTCPPorts = [ 80 ];
}
