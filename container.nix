{ config, pkgs, lib, ... }:

let
  davPathNginx = "/var/lib/webdav-nginx";
  davPathApache = "/var/lib/webdav-apache";
  davPathApacheLock = "/var/lib/httpd/dav";
in {
  services.nginx = {
    enable = true;
    package = pkgs.nginx.override {
      modules = with pkgs.nginxModules; [ dav ];
    };

    virtualHosts."_" = {
      locations = {
        "/" = {
          root = davPathNginx;
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

  services.httpd = {
    enable = true;
    adminAddr = "localhost";
    extraModules = [
      "dav"
      "dav_fs"
      "dav_lock"
    ];
    extraConfig = ''
      DAVLockDB ${davPathApacheLock}/lock
    '';
    virtualHosts = {
      "webdav" = {
        listen = [
          {
            ip = "*";
            port = 8000;
          }
        ];
        extraConfig = ''
          ServerAlias *
          DocumentRoot ${davPathApache}
          Alias /webdav ${davPathApache}
          <Directory ${davPathApache}>
            Order allow,deny
            Allow from all
            Require all granted

            Options Indexes
            DAV On
          </Directory>
        '';
      };
    };
  };

  systemd.services.nginx = {
    serviceConfig = {
      ReadWritePaths = davPathNginx;
    };
  };

  systemd.tmpfiles.rules = [
    "d ${davPathNginx} 0770 nginx nginx -"
    "d ${davPathApache} 0770 wwwrun wwwrun -"

    "d ${davPathApacheLock} 0770 wwwrun wwwrun"
  ];

  networking.firewall.allowedTCPPorts = [ 80 8000 ];
}
