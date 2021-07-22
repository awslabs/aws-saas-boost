$name = $args[0]
$res = docker ps -q -f name=$name
Write-Host $res
if(!$res){
	Write-Host No container with name $name found
} else {
	Write-Host Removing $name container with id: $res
	docker rm --force $name
	Write-Host Removed $name container with id: $res
}
