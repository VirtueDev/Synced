<html>
<head>
  <title>Restart Tool</title>
</head>
<body>
</body>
<footer>
</footer>
</html>
<?php
//function to check if a website is offline or online
function url_test( $url ) {
  $timeout = 10;
  $ch = curl_init();
  curl_setopt ( $ch, CURLOPT_URL, $url );
  curl_setopt ( $ch, CURLOPT_RETURNTRANSFER, 1 );
  curl_setopt ( $ch, CURLOPT_TIMEOUT, $timeout );
  $http_respond = curl_exec($ch);
  $http_respond = trim( strip_tags( $http_respond ) );
  $http_code = curl_getinfo( $ch, CURLINFO_HTTP_CODE );
  if ( ( $http_code == "200" ) || ( $http_code == "302" ) ) {
	return true;
  } else {
	// return $http_code;, possible too
	return false;
  }
  curl_close( $ch );
}

$website = "http://www.syncedonline.com/";
if( !url_test( $website ) ) {
	shell_exec("sudo fuser -k -n tcp 80 ; sudo /root/msoy/bin/msoyserver");
	echo('<p style="color: red">'.$website.' is OFFLINE! Automatically restarting the server... Please refresh this page
	in 5 to 10 seconds to check if the server is online.</p>'); 
}
else {
	echo('<p style="color: green">'.$website.' is ONLINE and working! No need to do anything!"</p>'); 
}
exit();
?>
